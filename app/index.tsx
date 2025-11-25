import { Readability } from '@mozilla/readability';
import * as FileSystem from 'expo-file-system/legacy';
import { Stack, useRouter } from 'expo-router';
import { parseHTML } from 'linkedom';
import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, AppState, Dimensions, Modal, Platform, ScrollView, StatusBar, StyleSheet, Text, TextInput, TouchableOpacity, useColorScheme, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import EpaperConnectivity from '../modules/epaper-connectivity';
import { createFolder, fetchList, fetchStatus, StorageStatus, uploadFile } from '../utils/fileManagerApi';

// macOS Theme Colors
const Colors = {
  light: {
    windowBg: '#F3F4F6', // gray-100
    headerBg: '#E5E7EB', // gray-200
    contentBg: '#FFFFFF',
    text: '#374151', // gray-700
    subText: '#9CA3AF', // gray-400
    border: '#D1D5DB', // gray-300
    primary: '#3B82F6', // blue-500
    success: '#22C55E',
    error: '#EF4444',
  },
  dark: {
    windowBg: '#1F2937', // gray-800
    headerBg: '#111827', // gray-900
    contentBg: '#111827',
    text: '#E5E7EB', // gray-200
    subText: '#6B7280', // gray-500
    border: '#374151', // gray-700
    primary: '#60A5FA', // blue-400
    success: '#22C55E',
    error: '#EF4444',
  },
};

const BASE_URL = 'http://192.168.3.3';

export default function App() {
  const colorScheme = useColorScheme();
  const isDark = colorScheme === 'dark';
  const theme = isDark ? Colors.dark : Colors.light;
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [isConnected, setIsConnected] = useState(false);
  const [isBound, setIsBound] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [storageStatus, setStorageStatus] = useState<StorageStatus | null>(null);
  const appState = useRef(AppState.currentState);

  // Quick Link State
  const [quickLinkVisible, setQuickLinkVisible] = useState(false);
  const [quickLinkUrl, setQuickLinkUrl] = useState('');
  const [converting, setConverting] = useState(false);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextAppState => {
      if (appState.current.match(/inactive|background/) && nextAppState === 'active') {
        checkConnection();
      }
      appState.current = nextAppState;
    });

    checkConnection();
    const interval = setInterval(() => {
      if (!isConnected && !isConnecting && !converting) {
        handleConnect(true);
      }
    }, 5000);

    return () => {
      subscription.remove();
      clearInterval(interval);
    };
  }, [isConnected, isConnecting, converting]);

  useEffect(() => {
    if (isConnected) {
      updateDeviceStatus();
    }
  }, [isConnected]);

  const checkConnection = async () => {
    try {
      const connected = await EpaperConnectivity.isConnected();
      setIsConnected(connected);
    } catch (error) {
      console.error('Connection check failed:', error);
    }
  };

  const updateDeviceStatus = async () => {
    try {
      if (Platform.OS === 'android' && !isBound) {
        await EpaperConnectivity.bindToEpaperNetwork();
        setIsBound(true);
        // Wait a moment for network binding to stabilize
        await new Promise(resolve => setTimeout(resolve, 1000));
      }

      const status = await fetchStatus(BASE_URL);
      setStorageStatus(status);
    } catch (error) {
      console.warn('Failed to fetch device status:', error);
    }
  };

  const handleConnect = async (silent = false) => {
    if (isConnecting) return;
    setIsConnecting(true);
    try {
      const success = await EpaperConnectivity.connectToEpaperHotspot();
      if (success) {
        setIsConnected(true);
        if (!silent) Alert.alert('Success', 'Connected to E-Paper');

        // Bind network immediately after connection
        if (Platform.OS === 'android') {
          await EpaperConnectivity.bindToEpaperNetwork();
          setIsBound(true);
        }

        // Fetch status after a short delay to allow network to stabilize
        setTimeout(() => {
          updateDeviceStatus();
        }, 1000);
      } else if (!silent) {
        // Only show error on manual attempt
        // Alert.alert('Error', 'Failed to connect'); 
      }
    } catch (error: any) {
      if (!silent) Alert.alert('Error', error.message || 'Connection failed');
    } finally {
      setIsConnecting(false);
    }
  };

  const handleAction = (action: string) => {
    if (action === 'File Manager') {
      router.push('/file-manager');
    } else if (action === 'Quick Link') {
      setQuickLinkVisible(true);
    } else {
      Alert.alert('Action', `Clicked: ${action}`);
    }
  };

  const handleConvertAndUpload = async () => {
    if (!quickLinkUrl) return;

    if (Platform.OS === 'web') {
      Alert.alert('Not Supported', 'URL conversion is not supported on Web due to CORS restrictions.');
      return;
    }

    setConverting(true);
    try {
      // 0. Unbind from E-Paper network to allow Internet access
      if (Platform.OS === 'android') {
        await EpaperConnectivity.unbindNetwork();
        // Give it a moment to switch networks
        await new Promise(resolve => setTimeout(resolve, 3000));
      }

      // 1. Fetch HTML content
      const response = await fetch(quickLinkUrl);
      const html = await response.text();

      // 2. Parse and Extract using Readability
      const { document } = parseHTML(html);
      const reader = new Readability(document);
      const article = reader.parse();

      if (!article) throw new Error('Could not parse article content');

      // 3. Send to dotEPUB API
      const formData = new FormData();
      formData.append('html', article.content || '');
      formData.append('title', article.title || 'Untitled');
      formData.append('url', quickLinkUrl);
      formData.append('lang', 'en');
      formData.append('format', 'epub');
      formData.append('author', article.byline || '');
      formData.append('links', '0'); // Immersive mode (remove links)
      formData.append('imgs', '1'); // Include images
      formData.append('flags', '|');

      const convertRes = await fetch('https://dotepub.com/api/v1/post', {
        method: 'POST',
        body: formData,
      });

      if (!convertRes.ok) {
        const text = await convertRes.text();
        console.error('dotEPUB error:', text);
        throw new Error('Conversion failed');
      }

      // 4. Save the binary response
      const blob = await convertRes.blob();
      const base64Data = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.readAsDataURL(blob);
        reader.onloadend = () => {
          const base64 = (reader.result as string).split(',')[1];
          resolve(base64);
        };
        reader.onerror = reject;
      });

      // Prioritize metadata titles
      const ogTitle = document.querySelector('meta[property="og:title"]')?.getAttribute('content');
      const docTitle = document.title;

      let rawTitle = ogTitle || docTitle || article.title || 'Untitled';
      try {
        const fixedTitle = rawTitle.replace(/%(?![0-9a-fA-F]{2})/g, '%25');
        rawTitle = decodeURIComponent(fixedTitle);
      } catch (e) {
        console.warn('Title decoding failed:', e);
      }

      const sanitizedTitle = rawTitle
        .replace(/[<>:"/\\|?*]/g, '')
        .trim()
        .substring(0, 50);

      const fileName = `${sanitizedTitle}.epub`;
      const tempFileName = `temp_${Date.now()}.epub`;
      const tempFileUri = `${FileSystem.cacheDirectory}${tempFileName}`;

      await FileSystem.writeAsStringAsync(tempFileUri, base64Data, { encoding: 'base64' });

      // 5. Re-bind to E-Paper network to upload
      if (Platform.OS === 'android') {
        await EpaperConnectivity.bindToEpaperNetwork();
        // Give it a moment to stabilize
        await new Promise(resolve => setTimeout(resolve, 1000));
      }

      // Check/Create /books folder
      const rootFiles = await fetchList(BASE_URL, '/');
      const booksFolderExists = rootFiles.some(f => f.name === 'books' && f.type === 'dir');

      if (!booksFolderExists) {
        await createFolder(BASE_URL, '/books');
      }

      const targetPath = `/books/${fileName}`;
      await uploadFile(BASE_URL, tempFileUri, fileName, targetPath);

      // Cleanup
      await FileSystem.deleteAsync(tempFileUri, { idempotent: true });

      Alert.alert('Success', 'Article converted and saved to /books');
      setQuickLinkVisible(false);
      setQuickLinkUrl('');
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'Failed to convert or upload article');

      // Ensure we re-bind if we failed during the internet phase
      if (Platform.OS === 'android') {
        EpaperConnectivity.bindToEpaperNetwork().catch(console.error);
      }
    } finally {
      setConverting(false);
    }
  };

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const getFreeSpace = () => {
    if (!storageStatus) return 'Unknown';
    const free = storageStatus.totalBytes - storageStatus.usedBytes;
    return formatBytes(free);
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.windowBg }]}>
      <Stack.Screen options={{ headerShown: false }} />
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} translucent backgroundColor="transparent" />

      {/* Main Window */}
      <View style={[styles.window, { backgroundColor: theme.contentBg }]}>

        {/* Window Header */}
        <View style={[styles.header, {
          backgroundColor: theme.headerBg,
          borderBottomColor: theme.border,
          paddingTop: insets.top + 10
        }]}>
        </View>

        <ScrollView contentContainerStyle={styles.content}>

          {/* Status Widget */}
          <View style={[styles.widget, { borderColor: theme.border, backgroundColor: theme.windowBg }]}>
            <View style={styles.widgetHeader}>
              <Text style={[styles.widgetTitle, { color: theme.text }]}>Device Status</Text>
              <View style={[styles.statusIndicator, { backgroundColor: isConnected ? theme.success : theme.error }]} />
            </View>

            <View style={styles.statusRow}>
              <Text style={[styles.statusLabel, { color: theme.subText }]}>Connection</Text>
              <Text style={[styles.statusValue, { color: isConnected ? theme.success : theme.error }]}>
                {isConnected ? 'Connected' : 'Disconnected'}
              </Text>
            </View>
            <View style={styles.statusRow}>
              <Text style={[styles.statusLabel, { color: theme.subText }]}>Firmware</Text>
              <Text style={[styles.statusValue, { color: theme.text }]}>
                {storageStatus?.version || 'Unknown'}
              </Text>
            </View>
            <View style={styles.statusRow}>
              <Text style={[styles.statusLabel, { color: theme.subText }]}>Free Space</Text>
              <Text style={[styles.statusValue, { color: theme.text }]}>
                {getFreeSpace()}
              </Text>
            </View>

            {!isConnected && (
              <TouchableOpacity
                style={[styles.connectButton, { backgroundColor: theme.primary }]}
                onPress={() => handleConnect(false)}
                disabled={isConnecting}
              >
                <Text style={styles.connectButtonText}>
                  {isConnecting ? 'Connecting...' : 'Connect Now'}
                </Text>
              </TouchableOpacity>
            )}
          </View>

          {/* Dock / Actions Grid */}
          <Text style={[styles.sectionTitle, { color: theme.subText }]}>Applications</Text>
          <View style={styles.dockGrid}>
            <TouchableOpacity
              style={[styles.dockItem, { backgroundColor: theme.windowBg, borderColor: theme.border }]}
              onPress={() => handleAction('File Manager')}
            >
              <Text style={styles.dockIcon}>📂</Text>
              <Text style={[styles.dockLabel, { color: theme.text }]}>File Manager</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.dockItem, { backgroundColor: theme.windowBg, borderColor: theme.border }]}
              onPress={() => handleAction('Convert Files')}
            >
              <Text style={styles.dockIcon}>📄</Text>
              <Text style={[styles.dockLabel, { color: theme.text }]}>Convert</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.dockItem, { backgroundColor: theme.windowBg, borderColor: theme.border }]}
              onPress={() => handleAction('Quick Link')}
            >
              <Text style={styles.dockIcon}>🔗</Text>
              <Text style={[styles.dockLabel, { color: theme.text }]}>Quick Link</Text>
            </TouchableOpacity>
          </View>

        </ScrollView>
      </View>

      {/* Quick Link Modal */}
      <Modal
        visible={quickLinkVisible}
        transparent={true}
        animationType="fade"
        onRequestClose={() => setQuickLinkVisible(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={[styles.modalContent, { backgroundColor: theme.contentBg }]}>
            <Text style={[styles.modalTitle, { color: theme.text }]}>Quick Link to EPUB</Text>
            <Text style={[styles.modalSubtitle, { color: theme.subText }]}>
              Enter a URL to convert it to an EPUB and upload it to your device.
            </Text>

            <TextInput
              style={[styles.input, { color: theme.text, borderColor: theme.border, backgroundColor: theme.windowBg }]}
              value={quickLinkUrl}
              onChangeText={setQuickLinkUrl}
              placeholder="https://example.com/article"
              placeholderTextColor={theme.subText}
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="url"
            />

            <View style={styles.modalButtons}>
              <TouchableOpacity
                style={[styles.modalButton, styles.cancelButton]}
                onPress={() => setQuickLinkVisible(false)}
                disabled={converting}
              >
                <Text style={styles.cancelButtonText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modalButton, { backgroundColor: theme.primary, opacity: converting ? 0.7 : 1 }]}
                onPress={handleConvertAndUpload}
                disabled={converting || !quickLinkUrl}
              >
                {converting ? (
                  <ActivityIndicator size="small" color="#FFFFFF" />
                ) : (
                  <Text style={styles.submitButtonText}>Send to Device</Text>
                )}
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  window: {
    flex: 1,
  },
  header: {
    paddingBottom: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 12,
    borderBottomWidth: 1,
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: '600',
  },
  content: {
    padding: 20,
  },
  widget: {
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    marginBottom: 30,
  },
  widgetHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  widgetTitle: {
    fontSize: 14,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  statusIndicator: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  statusLabel: {
    fontSize: 14,
  },
  statusValue: {
    fontSize: 14,
    fontWeight: '500',
  },
  connectButton: {
    marginTop: 12,
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
  },
  connectButtonText: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 1,
    marginBottom: 12,
    marginLeft: 4,
  },
  dockGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 16,
  },
  dockItem: {
    width: (Dimensions.get('window').width - 40 - 32 - 16) / 3, // Calculate width for 3 columns (approx)
    aspectRatio: 1,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 8,
  },
  dockIcon: {
    fontSize: 32,
    marginBottom: 8,
  },
  dockLabel: {
    fontSize: 12,
    fontWeight: '500',
    textAlign: 'center',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    padding: 20,
  },
  modalContent: {
    borderRadius: 12,
    padding: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.25,
    shadowRadius: 10,
    elevation: 10,
    maxWidth: 400,
    width: '100%',
    alignSelf: 'center',
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 8,
    textAlign: 'center',
  },
  modalSubtitle: {
    fontSize: 14,
    marginBottom: 20,
    textAlign: 'center',
    lineHeight: 20,
  },
  input: {
    borderWidth: 1,
    borderRadius: 6,
    padding: 10,
    fontSize: 14,
    marginBottom: 20,
  },
  modalButtons: {
    flexDirection: 'row',
    gap: 12,
  },
  modalButton: {
    flex: 1,
    padding: 10,
    borderRadius: 6,
    alignItems: 'center',
    justifyContent: 'center',
    height: 44,
  },
  cancelButton: {
    backgroundColor: '#9CA3AF',
  },
  cancelButtonText: {
    color: '#FFFFFF',
    fontWeight: '600',
    fontSize: 14,
  },
  submitButtonText: {
    color: '#FFFFFF',
    fontWeight: '600',
    fontSize: 14,
  },
});
