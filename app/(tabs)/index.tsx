import { useConfig } from '@/components/ConfigContext';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { FileCache } from '@/utils/cache';
import { copyToClipboard, downloadFile } from '@/utils/fileActions';
import { createFolder, deleteItem, fetchList, fetchStatus, FileItem, renameItem, StorageStatus, uploadFile } from '@/utils/fileManagerApi';
import { formatBytes } from '@/utils/format';
import { Readability } from '@mozilla/readability';
import * as DocumentPicker from 'expo-document-picker';
import { File, Paths } from 'expo-file-system';
import * as FileSystemLegacy from 'expo-file-system/legacy';
import * as Linking from 'expo-linking';
import { parseHTML } from 'linkedom';
import {
  ArrowLeft,
  CloudUpload,
  Copy,
  Download,
  FileText,
  Folder,
  Move,
  PlusCircle,
  Trash2,
  X
} from 'lucide-react-native';
import React, { useCallback, useEffect, useState } from 'react';
import { Alert, BackHandler, Modal, Platform, RefreshControl, ScrollView, StyleSheet, TextInput, TouchableOpacity, useColorScheme, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';



// --- Components ---


const CategoryItem = ({ Icon, color, label, count, onPress }: any) => {
  const colorScheme = useColorScheme();
  const isDark = colorScheme === 'dark';
  const itemBg = isDark ? '#1F2937' : '#F9FAFB';
  const textColor = isDark ? '#F9FAFB' : '#111827';

  return (
    <TouchableOpacity style={[styles.categoryItem, { backgroundColor: itemBg }]} onPress={onPress}>
      <View style={[styles.categoryIcon, { backgroundColor: color }]}>
        <Icon size={24} color="white" />
      </View>
      <View>
        <ThemedText style={[styles.categoryLabel, { color: textColor }]}>{label}</ThemedText>
        <ThemedText style={styles.categoryCount}>{count} items</ThemedText>
      </View>
    </TouchableOpacity>
  );
};

const FileListItem = ({ item, onPress, onLongPress }: any) => {
  const colorScheme = useColorScheme();
  const isDark = colorScheme === 'dark';
  const isDir = item.type === 'dir';

  // Dark mode colors
  const iconColor = isDir ? '#4ADE80' : '#60A5FA';
  const iconBg = isDark ? (isDir ? 'rgba(74, 222, 128, 0.2)' : 'rgba(96, 165, 250, 0.2)') : (isDir ? '#DCFCE7' : '#DBEAFE');
  const borderColor = isDark ? '#374151' : '#F3F4F6';

  return (
    <TouchableOpacity
      style={[styles.fileItem, { borderBottomColor: borderColor }]}
      onPress={onPress}
      onLongPress={onLongPress}
      delayLongPress={300}
    >
      <View style={[styles.fileIcon, { backgroundColor: iconBg }]}>
        {isDir ? (
          <Folder size={24} color={iconColor} fill={iconColor} fillOpacity={0.2} />
        ) : (
          <FileText size={24} color={iconColor} />
        )}
      </View>
      <View style={styles.fileInfo}>
        <ThemedText style={styles.fileName}>{item.name}</ThemedText>
        <ThemedText style={styles.fileMeta}>
          {isDir ? 'Folder' : formatBytes(item.size || 0)}
        </ThemedText>
      </View>
    </TouchableOpacity>
  );
};

// --- Main Screen ---

export default function FileManagerScreen() {
  const { baseUrl } = useConfig();
  const insets = useSafeAreaInsets();
  const colorScheme = useColorScheme();
  const isDark = colorScheme === 'dark';

  const [currentPath, setCurrentPath] = useState('/');
  const [items, setItems] = useState<FileItem[]>([]);
  const [status, setStatus] = useState<StorageStatus>({ totalBytes: 0, usedBytes: 0 });
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  // Modal State
  const [modalVisible, setModalVisible] = useState(false);
  const [modalMode, setModalMode] = useState<'create' | 'rename' | 'delete' | 'convert'>('create');
  const [inputValue, setInputValue] = useState('');
  const [selectedItem, setSelectedItem] = useState<FileItem | null>(null);

  // Context Menu State
  const [contextMenuVisible, setContextMenuVisible] = useState(false);

  const loadData = useCallback(async (path: string, forceRefresh = false) => {
    setLoading(true);

    // Try cache first
    if (!forceRefresh && FileCache.has(path)) {
      setItems(FileCache.get(path)!);
      // If root, we might still want to update status occasionally, but let's stick to on-demand for now
      // or maybe just fetch status if we are at root regardless of list cache?
      if (path === '/') {
        fetchStatus(baseUrl).then(setStatus).catch(() => { });
      }
      setLoading(false);
      return;
    }

    try {
      const listData = await fetchList(baseUrl, path);
      FileCache.set(path, listData);
      setItems(listData);

      if (path === '/') {
        const statusData = await fetchStatus(baseUrl);
        setStatus(statusData);
      }
    } catch (e) {
      console.error(e);
      // Keep old data if failed? or clear?
    } finally {
      setLoading(false);
    }
  }, [baseUrl]); // Removed status from dependencies

  useEffect(() => {
    loadData(currentPath);
  }, [currentPath, loadData]);

  // Handle Android Back Button
  useEffect(() => {
    const backAction = () => {
      if (currentPath !== '/') {
        // Go back logic
        const parentPath = currentPath.substring(0, currentPath.lastIndexOf('/')) || '/';
        setCurrentPath(parentPath);
        return true;
      }
      return false;
    };

    const backHandler = BackHandler.addEventListener(
      'hardwareBackPress',
      backAction
    );

    return () => backHandler.remove();
  }, [currentPath]);

  // Deep Link Handling
  useEffect(() => {
    const handleDeepLink = (event: { url: string }) => {
      const { url } = event;
      const parsed = Linking.parse(url);

      // Check for 'convert' path or query param 'url'
      // Example: hojo://convert?url=https://...
      // or just hojo://?url=https://...
      if (parsed.path === 'convert' || parsed.queryParams?.url) {
        const targetUrl = parsed.queryParams?.url;
        if (typeof targetUrl === 'string') {
          // Automatically trigger conversion
          handleConvertAndUpload(targetUrl);
        }
      }
    };

    const getInitialUrl = async () => {
      const url = await Linking.getInitialURL();
      if (url) handleDeepLink({ url });
    };

    getInitialUrl();
    const subscription = Linking.addEventListener('url', handleDeepLink);
    return () => subscription.remove();
  }, [baseUrl]); // Add baseUrl dependency if handleConvertAndUpload uses it (it does via closure, but better to be safe)

  const onRefresh = async () => {
    setRefreshing(true);
    await loadData(currentPath, true);
    setRefreshing(false);
  };

  const handleNavigate = (item: FileItem) => {
    if (item.type === 'dir') {
      const newPath = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`;
      setCurrentPath(newPath);
    } else {
      Alert.alert('File Info', `File: ${item.name}\nSize: ${formatBytes(item.size || 0)}`);
    }
  };

  const handleGoBack = () => {
    if (currentPath === '/') return;
    const parentPath = currentPath.substring(0, currentPath.lastIndexOf('/')) || '/';
    setCurrentPath(parentPath);
  };

  const handleCreateFolder = () => { setModalMode('create'); setInputValue(''); setModalVisible(true); };
  const handleConvertLink = () => { setModalMode('convert'); setInputValue(''); setModalVisible(true); };

  const handleUpload = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({ copyToCacheDirectory: true, multiple: false });
      if (result.canceled) return;
      const file = result.assets[0];
      const targetPath = currentPath === '/' ? `/${file.name}` : `${currentPath}/${file.name}`;
      setLoading(true);
      await uploadFile(baseUrl, file.uri, file.name, targetPath);
      FileCache.invalidate(currentPath); // Invalidate cache
      await loadData(currentPath, true);
      Alert.alert('Success', 'Uploaded');
    } catch (e) { Alert.alert('Error', 'Upload failed'); } finally { setLoading(false); }
  };

  const handleConvertAndUpload = async (url: string) => {
    if (!url) return;

    if (Platform.OS === 'web') {
      Alert.alert('Not Supported', 'URL conversion is not supported on Web due to CORS restrictions.');
      return;
    }

    setLoading(true);
    try {
      // 1. Fetch HTML content
      const response = await fetch(url);
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
      formData.append('url', url);
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

      // Convert blob to base64 to write to file
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

      console.log('Title Debug:', {
        ogTitle,
        docTitle,
        articleTitle: article.title,
        finalRawTitle: ogTitle || docTitle || article.title || 'Untitled'
      });

      let rawTitle = ogTitle || docTitle || article.title || 'Untitled';
      try {
        // Fix malformed URI components by escaping % not followed by hex
        const fixedTitle = rawTitle.replace(/%(?![0-9a-fA-F]{2})/g, '%25');
        rawTitle = decodeURIComponent(fixedTitle);
      } catch (e) {
        console.warn('Title decoding failed even after fix:', e);
        // Ignore error, use original title
      }

      const sanitizedTitle = rawTitle
        .replace(/[<>:"/\\|?*]/g, '') // Remove invalid characters
        .trim()
        .substring(0, 50); // Limit length

      const fileName = `${sanitizedTitle}.epub`;
      // Use temp filename without spaces
      const tempFileName = `temp_${Date.now()}.epub`;
      const tempFile = new File(Paths.cache, tempFileName);

      await FileSystemLegacy.writeAsStringAsync(tempFile.uri, base64Data, { encoding: FileSystemLegacy.EncodingType.Base64 });

      // targetPath still has the proper display name
      const targetPath = currentPath === '/' ? `/${fileName}` : `${currentPath}/${fileName}`;
      await uploadFile(baseUrl, tempFile.uri, fileName, targetPath);

      // Cleanup
      if (tempFile.exists) {
        tempFile.delete();
      }

      // Refresh current path
      FileCache.invalidate(currentPath);
      await loadData(currentPath, true);

      Alert.alert('Success', 'Article converted and saved');
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'Failed to convert or upload article');
    } finally {
      setLoading(false);
    }
  };

  const handleModalSubmit = async () => {
    setModalVisible(false);
    try {
      if (modalMode === 'create') {
        if (!inputValue.trim()) return;
        const path = currentPath === '/' ? `/${inputValue}/` : `${currentPath}/${inputValue}/`;
        await createFolder(baseUrl, path);
        FileCache.invalidate(currentPath);
      } else if (modalMode === 'rename' && selectedItem) {
        if (!inputValue.trim()) return;
        const oldPath = currentPath === '/' ? `/${selectedItem.name}` : `${currentPath}/${selectedItem.name}`;
        const newPath = currentPath === '/' ? `/${inputValue}` : `${currentPath}/${inputValue}`;
        await renameItem(baseUrl, oldPath, newPath);
        FileCache.invalidate(currentPath);
      } else if (modalMode === 'delete' && selectedItem) {
        const path = currentPath === '/' ? `/${selectedItem.name}` : `${currentPath}/${selectedItem.name}`;
        await deleteItem(baseUrl, path);
        FileCache.invalidate(currentPath);
        if (selectedItem.type === 'dir') {
          FileCache.invalidate(path);
        }
      } else if (modalMode === 'convert') {
        if (!inputValue.trim()) return;
        await handleConvertAndUpload(inputValue.trim());
        return; // handleConvertAndUpload handles its own refresh/alert
      }
      loadData(currentPath, true);
    } catch (e) { Alert.alert('Error', `Failed to ${modalMode}`); }
  };

  // Context Menu Actions
  const openContextMenu = (item: FileItem) => {
    setSelectedItem(item);
    setContextMenuVisible(true);
  };

  const handleMenuAction = async (action: 'delete' | 'move' | 'download' | 'copy') => {
    setContextMenuVisible(false);
    if (!selectedItem) return;

    switch (action) {
      case 'delete':
        setModalMode('delete');
        setModalVisible(true);
        break;
      case 'move':
        setModalMode('rename');
        setInputValue(selectedItem.name);
        setModalVisible(true);
        break;
      case 'download':
        try {
          await downloadFile(baseUrl, selectedItem, currentPath);
        } catch (e) {
          Alert.alert('Error', 'Download failed');
        }
        break;
      case 'copy':
        await copyToClipboard(selectedItem, currentPath);
        Alert.alert('Copied', 'Path copied to clipboard');
        break;
    }
  };

  const isRoot = currentPath === '/';
  const iconColor = isDark ? '#fff' : '#000';

  return (
    <ThemedView style={[styles.container, { paddingTop: insets.top }]}>
      {/* Top Navigation Bar */}
      <View style={styles.header}>
        {isRoot ? (
          <View />
        ) : (
          <View style={styles.navHeader}>
            <TouchableOpacity onPress={handleGoBack} style={styles.backButton}>
              <ArrowLeft size={24} color={iconColor} />
            </TouchableOpacity>
            <ThemedText style={styles.navTitle} numberOfLines={1}>{currentPath}</ThemedText>
            <View style={{ width: 40 }} />
          </View>
        )}

        <View style={styles.headerActions}>

          <TouchableOpacity onPress={handleUpload} style={styles.iconButton}>
            <CloudUpload size={22} color={iconColor} />
          </TouchableOpacity>
          <TouchableOpacity onPress={handleCreateFolder} style={styles.iconButton}>
            <PlusCircle size={22} color={iconColor} />
          </TouchableOpacity>
        </View>
      </View>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
      >

        <View style={styles.section}>
          <ThemedText style={styles.sectionTitle}>Tools</ThemedText>
          <View style={styles.grid}>
            <CategoryItem
              Icon={require('lucide-react-native').Image}
              color="#F59E0B"
              label="Wallpaper"
              count="Maker"
              onPress={() => require('expo-router').router.push({ pathname: '/wallpaper', params: { path: currentPath } })}
            />
            <CategoryItem
              Icon={require('lucide-react-native').Link}
              color="#3B82F6"
              label="URL to EPUB"
              count="Converter"
              onPress={handleConvertLink}
            />
          </View>
        </View>

        <View style={styles.section}>
          {items.length === 0 && !loading ? (
            <View style={styles.emptyState}>
              <ThemedText style={styles.emptyText}>No items</ThemedText>
            </View>
          ) : (
            items.map((item) => (
              <FileListItem
                key={item.name}
                item={item}
                onPress={() => handleNavigate(item)}
                onLongPress={() => openContextMenu(item)}
              />
            ))
          )}
        </View>
      </ScrollView>

      {/* Input Modal */}
      <Modal animationType="fade" transparent={true} visible={modalVisible} onRequestClose={() => setModalVisible(false)}>
        <View style={styles.modalOverlay}>
          <View style={[styles.modalContent, { backgroundColor: isDark ? '#1F2937' : 'white' }]}>
            <ThemedText style={[styles.modalTitle, { color: isDark ? '#fff' : '#000' }]}>
              {modalMode === 'create' ? 'New Folder' : modalMode === 'rename' ? 'Rename / Move' : modalMode === 'convert' ? 'Convert URL to EPUB' : 'Delete Item'}
            </ThemedText>
            {modalMode === 'delete' ? (
              <ThemedText style={{ color: isDark ? '#D1D5DB' : '#4B5563', marginBottom: 24, textAlign: 'center' }}>
                Are you sure you want to delete "{selectedItem?.name}"?
              </ThemedText>
            ) : (
              <TextInput
                style={[styles.modalInput, { borderColor: isDark ? '#374151' : '#ddd', color: isDark ? '#fff' : '#000' }]}
                value={inputValue}
                onChangeText={setInputValue}
                placeholder={modalMode === 'convert' ? "https://example.com/article" : "Name"}
                placeholderTextColor={isDark ? '#9CA3AF' : '#9CA3AF'}
                autoFocus
                autoCapitalize={modalMode === 'convert' ? 'none' : 'sentences'}
                keyboardType={modalMode === 'convert' ? 'url' : 'default'}
              />
            )}
            <View style={styles.modalButtons}>
              <TouchableOpacity style={[styles.modalButton, styles.cancelButton, { backgroundColor: isDark ? '#374151' : '#f5f5f5' }]} onPress={() => setModalVisible(false)}>
                <ThemedText style={{ color: isDark ? '#fff' : '#000' }}>Cancel</ThemedText>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modalButton, styles.submitButton, modalMode === 'delete' && { backgroundColor: '#EF4444' }]}
                onPress={handleModalSubmit}
              >
                <ThemedText style={{ color: 'white' }}>{modalMode === 'delete' ? 'Delete' : 'OK'}</ThemedText>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      {/* Context Menu Modal */}
      <Modal animationType="slide" transparent={true} visible={contextMenuVisible} onRequestClose={() => setContextMenuVisible(false)}>
        <TouchableOpacity style={styles.menuOverlay} activeOpacity={1} onPress={() => setContextMenuVisible(false)}>
          <View style={[styles.menuContent, { backgroundColor: isDark ? '#1F2937' : 'white' }]}>
            <View style={styles.menuHeader}>
              <ThemedText style={styles.menuTitle}>{selectedItem?.name}</ThemedText>
              <TouchableOpacity onPress={() => setContextMenuVisible(false)}>
                <X size={24} color={isDark ? '#fff' : '#000'} />
              </TouchableOpacity>
            </View>

            <TouchableOpacity style={styles.menuItem} onPress={() => handleMenuAction('copy')}>
              <Copy size={24} color={isDark ? '#fff' : '#000'} />
              <ThemedText style={styles.menuItemText}>Copy Path</ThemedText>
            </TouchableOpacity>

            <TouchableOpacity style={styles.menuItem} onPress={() => handleMenuAction('move')}>
              <Move size={24} color={isDark ? '#fff' : '#000'} />
              <ThemedText style={styles.menuItemText}>Rename / Move</ThemedText>
            </TouchableOpacity>

            <TouchableOpacity style={styles.menuItem} onPress={() => handleMenuAction('download')}>
              <Download size={24} color={isDark ? '#fff' : '#000'} />
              <ThemedText style={styles.menuItemText}>Download</ThemedText>
            </TouchableOpacity>

            <TouchableOpacity style={[styles.menuItem, styles.deleteItem]} onPress={() => handleMenuAction('delete')}>
              <Trash2 size={24} color="#EF4444" />
              <ThemedText style={[styles.menuItemText, { color: '#EF4444' }]}>Delete</ThemedText>
            </TouchableOpacity>
          </View>
        </TouchableOpacity>
      </Modal>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingVertical: 12,
  },

  navHeader: { flexDirection: 'row', alignItems: 'center', flex: 1 },
  backButton: { marginRight: 12 },
  navTitle: { fontSize: 18, fontWeight: '600', flex: 1 },

  headerActions: { flexDirection: 'row', gap: 12 },
  iconButton: { padding: 4 },

  scrollContent: { padding: 16, paddingBottom: 40 },

  // Storage Widget
  storageCard: {
    borderRadius: 24, padding: 20,
    flexDirection: 'row', alignItems: 'center', marginBottom: 24,
  },
  storageChart: { position: 'relative', width: 80, height: 80, marginRight: 16 },
  chartTextContainer: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, justifyContent: 'center', alignItems: 'center' },
  chartText: { fontSize: 16, fontWeight: 'bold', color: '#4ADE80' },
  storageInfo: { flex: 1 },
  storageTitle: { fontSize: 16, fontWeight: '600', marginBottom: 4 },
  storageSubtitle: { fontSize: 12, color: '#9CA3AF' },
  storageFree: {},
  freeText: { fontSize: 16, fontWeight: 'bold' },

  // Grid
  section: { marginBottom: 24 },
  sectionTitle: { fontSize: 18, fontWeight: '600', marginBottom: 16 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  categoryItem: {
    width: '48%', borderRadius: 16, padding: 16,
    flexDirection: 'row', alignItems: 'center', gap: 12,
  },
  categoryIcon: { width: 40, height: 40, borderRadius: 12, justifyContent: 'center', alignItems: 'center' },
  categoryLabel: { fontSize: 14, fontWeight: '600' },
  categoryCount: { fontSize: 11, color: '#9CA3AF' },

  // List
  fileItem: {
    flexDirection: 'row', alignItems: 'center', paddingVertical: 12,
    borderBottomWidth: 1,
  },
  fileIcon: { width: 48, height: 48, borderRadius: 12, justifyContent: 'center', alignItems: 'center', marginRight: 16 },
  fileInfo: { flex: 1 },
  fileName: { fontSize: 16, fontWeight: '500' },
  fileMeta: { fontSize: 12, color: '#9CA3AF', marginTop: 2 },

  emptyState: { padding: 20, alignItems: 'center' },
  emptyText: { color: '#9CA3AF' },

  // Modal
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', alignItems: 'center' },
  modalContent: { width: '80%', borderRadius: 16, padding: 24 },
  modalTitle: { fontSize: 20, fontWeight: 'bold', marginBottom: 16, textAlign: 'center' },
  modalInput: { borderWidth: 1, borderRadius: 8, padding: 12, marginBottom: 24 },
  modalButtons: { flexDirection: 'row', gap: 12 },
  modalButton: { flex: 1, padding: 12, borderRadius: 8, alignItems: 'center' },
  cancelButton: {},
  submitButton: { backgroundColor: '#007AFF' },

  // Context Menu
  menuOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' },
  menuContent: { borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 24, paddingBottom: 40 },
  menuHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  menuTitle: { fontSize: 18, fontWeight: 'bold' },
  menuItem: { flexDirection: 'row', alignItems: 'center', paddingVertical: 16, gap: 16 },
  menuItemText: { fontSize: 16, fontWeight: '500' },
  deleteItem: { marginTop: 8, borderTopWidth: 1, borderTopColor: '#eee', paddingTop: 24 },
});
