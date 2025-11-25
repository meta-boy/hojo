import { useSafeAreaInsets } from 'react-native-safe-area-context';

// ...

export default function FileManager() {
    const router = useRouter();
    const insets = useSafeAreaInsets();
    // ...

    // ...
    return (
        <View style={[styles.container, { backgroundColor: theme.windowBg }]}>
            {/* ... */}

            {/* Main Window Container */}
            <View style={[styles.window, { backgroundColor: theme.contentBg }]}>

                {/* Window Header */}
                <View style={[styles.header, {
                    backgroundColor: theme.headerBg,
                    borderBottomColor: theme.border,
                    paddingTop: insets.top + 10, // Safe area + extra padding
                }]}>
                    {/* ... */}
                </View>
                {/* ... */}
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    // ...
    header: {
        // paddingTop removed from here, handled inline
        paddingBottom: 10,
        flexDirection: 'row',
        alignItems: 'center',
        paddingHorizontal: 12,
        borderBottomWidth: 1,
    },
    // ...
});

const BASE_URL = 'http://192.168.3.3';

// macOS Theme Colors
const Colors = {
    light: {
        windowBg: '#F3F4F6', // gray-100
        headerBg: '#E5E7EB', // gray-200
        sidebarBg: '#F9FAFB', // gray-50
        contentBg: '#FFFFFF',
        text: '#374151', // gray-700
        subText: '#9CA3AF', // gray-400
        border: '#D1D5DB', // gray-300
        selection: '#E4EFFD',
        selectionBorder: '#BFDBFE',
        primary: '#3B82F6', // blue-500
        danger: '#EF4444', // red-500
    },
    dark: {
        windowBg: '#1F2937', // gray-800
        headerBg: '#111827', // gray-900
        sidebarBg: '#1F2937',
        contentBg: '#111827',
        text: '#E5E7EB', // gray-200
        subText: '#6B7280', // gray-500
        border: '#374151', // gray-700
        selection: '#1E3A8A', // blue-900
        selectionBorder: '#1D4ED8',
        primary: '#60A5FA', // blue-400
        danger: '#F87171', // red-400
    },
};

// Icons
const IconBack = ({ color = '#374151' }) => (
    <Svg width="20" height="20" fill="none" viewBox="0 0 24 24" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <Path d="M15 19l-7-7 7-7" />
    </Svg>
);

const IconRefresh = ({ color = '#374151' }) => (
    <Svg width="18" height="18" fill="none" viewBox="0 0 24 24" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <Path d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
    </Svg>
);

const IconNewFolder = ({ color = '#374151' }) => (
    <Svg width="18" height="18" fill="none" viewBox="0 0 24 24" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <Path d="M9 13h6m-3-3v6m-9 1V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
    </Svg>
);

const IconUpload = ({ color = '#374151' }) => (
    <Svg width="18" height="18" fill="none" viewBox="0 0 24 24" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <Path d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
    </Svg>
);

export default function FileManager() {
    const router = useRouter();
    const colorScheme = useColorScheme();
    const isDark = colorScheme === 'dark';
    const theme = isDark ? Colors.dark : Colors.light;

    const [currentPath, setCurrentPath] = useState('/');
    const [files, setFiles] = useState<FileItem[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [storageInfo, setStorageInfo] = useState<StorageStatus | null>(null);

    // Modal State
    const [modalVisible, setModalVisible] = useState(false);
    const [modalMode, setModalMode] = useState<'create' | 'rename'>('create');
    const [inputText, setInputText] = useState('');
    const [selectedItem, setSelectedItem] = useState<FileItem | null>(null);

    useEffect(() => {
        loadFiles();
        loadStatus();

        const backAction = () => {
            if (currentPath !== '/') {
                handleGoBack();
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

    const loadFiles = async () => {
        setIsLoading(true);
        try {
            const list = await fetchList(BASE_URL, currentPath);
            setFiles(list);
        } catch (error) {
            Alert.alert('Error', 'Failed to load files');
        } finally {
            setIsLoading(false);
            setRefreshing(false);
        }
    };

    const loadStatus = async () => {
        try {
            const status = await fetchStatus(BASE_URL);
            setStorageInfo(status);
        } catch (e) {
            console.warn('Failed to load status');
        }
    };

    const handleRefresh = () => {
        setRefreshing(true);
        loadFiles();
        loadStatus();
    };

    const handleNavigate = (item: FileItem) => {
        if (item.type === 'dir') {
            const newPath = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`;
            setCurrentPath(newPath);
        } else {
            Alert.alert('File Info', `Name: ${item.name}\nSize: ${item.size} bytes`);
        }
    };

    const handleGoBack = () => {
        if (currentPath === '/') {
            router.back();
        } else {
            const parts = currentPath.split('/');
            parts.pop();
            const newPath = parts.join('/') || '/';
            setCurrentPath(newPath);
        }
    };

    const handleCreateFolder = () => {
        setModalMode('create');
        setInputText('');
        setModalVisible(true);
    };

    const handleRename = (item: FileItem) => {
        setModalMode('rename');
        setSelectedItem(item);
        setInputText(item.name);
        setModalVisible(true);
    };

    const handleDelete = (item: FileItem) => {
        Alert.alert(
            'Delete Item',
            `Are you sure you want to delete "${item.name}"?`,
            [
                { text: 'Cancel', style: 'cancel' },
                {
                    text: 'Delete',
                    style: 'destructive',
                    onPress: async () => {
                        try {
                            const itemPath = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`;
                            await deleteItem(BASE_URL, itemPath);
                            loadFiles();
                            loadStatus();
                        } catch (error) {
                            Alert.alert('Error', 'Failed to delete item');
                        }
                    },
                },
            ]
        );
    };

    const handleUpload = async () => {
        try {
            const result = await DocumentPicker.getDocumentAsync({
                copyToCacheDirectory: true,
            });

            if (result.canceled) return;

            const asset = result.assets[0];
            const targetPath = currentPath === '/' ? `/${asset.name}` : `${currentPath}/${asset.name}`;

            setIsLoading(true);
            await uploadFile(BASE_URL, asset.uri, asset.name, targetPath);
            Alert.alert('Success', 'File uploaded successfully');
            loadFiles();
            loadStatus();
        } catch (error) {
            Alert.alert('Error', 'Failed to upload file');
        } finally {
            setIsLoading(false);
        }
    };

    const handleModalSubmit = async () => {
        if (!inputText.trim()) return;

        try {
            if (modalMode === 'create') {
                const newPath = currentPath === '/' ? `/${inputText}` : `${currentPath}/${inputText}`;
                await createFolder(BASE_URL, newPath);
            } else if (modalMode === 'rename' && selectedItem) {
                const oldPath = currentPath === '/' ? `/${selectedItem.name}` : `${currentPath}/${selectedItem.name}`;
                const newPath = currentPath === '/' ? `/${inputText}` : `${currentPath}/${inputText}`;
                await renameItem(BASE_URL, oldPath, newPath);
            }
            setModalVisible(false);
            loadFiles();
        } catch (error) {
            Alert.alert('Error', `Failed to ${modalMode} item`);
        }
    };

    const renderItem = ({ item }: { item: FileItem }) => (
        <TouchableOpacity
            style={[styles.gridItem, { borderColor: 'transparent' }]}
            onPress={() => handleNavigate(item)}
            onLongPress={() => {
                Alert.alert(
                    'Options',
                    item.name,
                    [
                        { text: 'Rename', onPress: () => handleRename(item) },
                        { text: 'Delete', onPress: () => handleDelete(item), style: 'destructive' },
                        { text: 'Cancel', style: 'cancel' },
                    ]
                );
            }}
        >
            <View style={styles.iconContainer}>
                <Text style={styles.gridIcon}>{item.type === 'dir' ? '📁' : '📄'}</Text>
            </View>
            <Text style={[styles.gridItemName, { color: theme.text }]} numberOfLines={2}>
                {item.name}
            </Text>
        </TouchableOpacity>
    );

    const getStoragePercentage = () => {
        if (!storageInfo || storageInfo.totalBytes === 0) return 0;
        return (storageInfo.usedBytes / storageInfo.totalBytes) * 100;
    };

    return (
        <View style={[styles.container, { backgroundColor: theme.windowBg }]}>
            <Stack.Screen options={{ headerShown: false }} />
            <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} translucent backgroundColor="transparent" />

            {/* Main Window Container */}
            <View style={[styles.window, { backgroundColor: theme.contentBg }]}>

                {/* Window Header */}
                <View style={[styles.header, { backgroundColor: theme.headerBg, borderBottomColor: theme.border }]}>

                    {/* Path Display */}
                    <View style={[styles.pathContainer, { backgroundColor: theme.contentBg, borderColor: theme.border }]}>
                        <Text style={styles.folderIcon}>📂</Text>
                        <Text style={[styles.pathText, { color: theme.text }]} numberOfLines={1}>
                            {currentPath}
                        </Text>
                    </View>

                    {/* Actions */}
                    <View style={styles.headerActions}>
                        <TouchableOpacity onPress={handleGoBack} style={styles.iconButton}>
                            <IconBack color={theme.text} />
                        </TouchableOpacity>
                        <TouchableOpacity onPress={handleRefresh} style={styles.iconButton}>
                            <IconRefresh color={theme.text} />
                        </TouchableOpacity>
                    </View>
                </View>

                {/* Toolbar (Secondary Header) */}
                <View style={[styles.toolbar, { borderBottomColor: theme.border }]}>
                    <TouchableOpacity style={styles.toolbarButton} onPress={handleCreateFolder}>
                        <IconNewFolder color={theme.text} />
                        <Text style={[styles.toolbarText, { color: theme.text }]}>New Folder</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={styles.toolbarButton} onPress={handleUpload}>
                        <IconUpload color={theme.text} />
                        <Text style={[styles.toolbarText, { color: theme.text }]}>Upload</Text>
                    </TouchableOpacity>

                    {/* Storage Bar (Mini) */}
                    <View style={styles.storageContainer}>
                        <View style={[styles.storageBarBg, { backgroundColor: theme.border }]}>
                            <View
                                style={[styles.storageBarFill, {
                                    backgroundColor: theme.primary,
                                    width: `${getStoragePercentage()}%`
                                }]}
                            />
                        </View>
                        <Text style={[styles.storageText, { color: theme.subText }]}>
                            {storageInfo ? `${(storageInfo.usedBytes / 1024 / 1024).toFixed(1)}MB used` : 'Storage'}
                        </Text>
                    </View>
                </View>

                {/* File Grid */}
                <FlatList
                    data={files}
                    renderItem={renderItem}
                    keyExtractor={(item) => item.name}
                    numColumns={4} // Grid layout
                    contentContainerStyle={styles.gridContent}
                    columnWrapperStyle={styles.columnWrapper}
                    refreshing={refreshing}
                    onRefresh={handleRefresh}
                    ListEmptyComponent={
                        !isLoading ? (
                            <View style={styles.emptyContainer}>
                                <Text style={{ fontSize: 40, marginBottom: 10 }}>📭</Text>
                                <Text style={[styles.emptyText, { color: theme.subText }]}>Empty Folder</Text>
                            </View>
                        ) : null
                    }
                />

                {isLoading && !refreshing && (
                    <View style={styles.loadingOverlay}>
                        <ActivityIndicator size="large" color={theme.primary} />
                    </View>
                )}

            </View>

            {/* Input Modal */}
            <Modal
                visible={modalVisible}
                transparent={true}
                animationType="fade"
                onRequestClose={() => setModalVisible(false)}
            >
                <View style={styles.modalOverlay}>
                    <View style={[styles.modalContent, { backgroundColor: theme.contentBg }]}>
                        <Text style={[styles.modalTitle, { color: theme.text }]}>
                            {modalMode === 'create' ? 'New Folder' : 'Rename Item'}
                        </Text>
                        <TextInput
                            style={[styles.input, { color: theme.text, borderColor: theme.border, backgroundColor: theme.windowBg }]}
                            value={inputText}
                            onChangeText={setInputText}
                            placeholder={modalMode === 'create' ? "Folder Name" : "New Name"}
                            placeholderTextColor={theme.subText}
                            autoFocus
                        />
                        <View style={styles.modalButtons}>
                            <TouchableOpacity
                                style={[styles.modalButton, styles.cancelButton]}
                                onPress={() => setModalVisible(false)}
                            >
                                <Text style={styles.cancelButtonText}>Cancel</Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                                style={[styles.modalButton, { backgroundColor: theme.primary }]}
                                onPress={handleModalSubmit}
                            >
                                <Text style={styles.submitButtonText}>
                                    {modalMode === 'create' ? 'Create' : 'Rename'}
                                </Text>
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
        paddingHorizontal: 12,
        borderBottomWidth: 1,
    },
    pathContainer: {
        flex: 1,
        flexDirection: 'row',
        alignItems: 'center',
        height: 30,
        borderRadius: 6,
        borderWidth: 1,
        paddingHorizontal: 8,
        marginRight: 12,
    },
    folderIcon: {
        fontSize: 14,
        marginRight: 6,
    },
    pathText: {
        fontSize: 13,
        fontWeight: '500',
    },
    headerActions: {
        flexDirection: 'row',
        gap: 8,
    },
    iconButton: {
        padding: 4,
    },
    toolbar: {
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: 8,
        paddingHorizontal: 12,
        borderBottomWidth: 1,
        gap: 16,
    },
    toolbarButton: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
    },
    toolbarIcon: {
        fontSize: 16,
    },
    toolbarText: {
        fontSize: 13,
        fontWeight: '500',
    },
    storageContainer: {
        marginLeft: 'auto',
        width: 100,
    },
    storageBarBg: {
        height: 4,
        borderRadius: 2,
        marginBottom: 2,
        overflow: 'hidden',
    },
    storageBarFill: {
        height: '100%',
        borderRadius: 2,
    },
    storageText: {
        fontSize: 10,
        textAlign: 'right',
    },
    gridContent: {
        padding: 12,
    },
    columnWrapper: {
        gap: 12,
    },
    gridItem: {
        flex: 1,
        alignItems: 'center',
        marginBottom: 16,
        padding: 8,
        borderRadius: 6,
    },
    iconContainer: {
        marginBottom: 4,
    },
    gridIcon: {
        fontSize: 48,
    },
    gridItemName: {
        fontSize: 12,
        textAlign: 'center',
        lineHeight: 16,
    },
    emptyContainer: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
        paddingTop: 100,
    },
    emptyText: {
        fontSize: 16,
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
        marginBottom: 16,
        textAlign: 'center',
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
    loadingOverlay: {
        ...StyleSheet.absoluteFillObject,
        backgroundColor: 'rgba(255,255,255,0.5)',
        justifyContent: 'center',
        alignItems: 'center',
    },
});
