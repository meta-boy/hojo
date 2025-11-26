import * as Clipboard from 'expo-clipboard';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import { Platform } from 'react-native';
import { FileItem } from './fileManagerApi';

export const downloadFile = async (baseUrl: string, item: FileItem, currentPath: string): Promise<void> => {
    const path = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`;
    const fileUri = `${FileSystem.documentDirectory}${item.name}`;

    if (Platform.OS === 'web') {
        const link = document.createElement('a');
        link.href = `${baseUrl}${path}`;
        link.download = item.name;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        return;
    }

    try {
        const downloadRes = await FileSystem.downloadAsync(
            `${baseUrl}${path}`,
            fileUri
        );

        if (downloadRes.status === 200) {
            if (await Sharing.isAvailableAsync()) {
                await Sharing.shareAsync(downloadRes.uri);
            } else {
                console.log('Sharing not available');
            }
        } else {
            throw new Error(`Download failed with status ${downloadRes.status}`);
        }
    } catch (e) {
        console.error('Download failed', e);
        throw e;
    }
};

export const copyToClipboard = async (item: FileItem, currentPath: string) => {
    const path = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`;
    await Clipboard.setStringAsync(path);
};
