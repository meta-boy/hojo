import * as DocumentPicker from 'expo-document-picker';
import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, BackHandler } from 'react-native';
import { createFolder, deleteItem, fetchList, fetchStatus, FileItem, renameItem, StorageStatus, uploadFile } from '../utils/fileManagerApi';

export function useFileManager(BASE_URL: string) {
    const router = useRouter();
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

    return {
        currentPath,
        files,
        isLoading,
        refreshing,
        storageInfo,
        modalVisible,
        setModalVisible,
        modalMode,
        inputText,
        setInputText,
        handleRefresh,
        handleNavigate,
        handleGoBack,
        handleCreateFolder,
        handleRename,
        handleDelete,
        handleUpload,
        handleModalSubmit
    };
}
