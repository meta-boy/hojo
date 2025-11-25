import { Stack, useRouter } from 'expo-router';
import React, { useEffect, useRef, useState } from 'react';
import { Alert, AppState, Dimensions, Platform, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, useColorScheme, View } from 'react-native';
import EpaperConnectivity from '../modules/epaper-connectivity';
import { fetchStatus, StorageStatus } from '../utils/fileManagerApi';

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

export default function App() {
  const colorScheme = useColorScheme();
  const isDark = colorScheme === 'dark';
  const theme = isDark ? Colors.dark : Colors.light;
  const router = useRouter();

  const [isConnected, setIsConnected] = useState(false);
  const [isBound, setIsBound] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [storageStatus, setStorageStatus] = useState<StorageStatus | null>(null);
  const appState = useRef(AppState.currentState);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextAppState => {
      if (appState.current.match(/inactive|background/) && nextAppState === 'active') {
        checkConnection();
      }
      appState.current = nextAppState;
    });

    checkConnection();
    const interval = setInterval(() => {
      if (!isConnected && !isConnecting) {
        handleConnect(true);
      }
    }, 5000);

    return () => {
      subscription.remove();
      clearInterval(interval);
    };
  }, [isConnected, isConnecting]);

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

      const status = await fetchStatus('http://192.168.3.3');
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
    } else {
      Alert.alert('Action', `Clicked: ${action}`);
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
        <View style={[styles.header, { backgroundColor: theme.headerBg, borderBottomColor: theme.border }]}>
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
    paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 50,
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
});
