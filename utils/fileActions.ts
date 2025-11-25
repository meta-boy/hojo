import * as Clipboard from 'expo-clipboard';
import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { Platform } from 'react-native';
import { FileItem } from './fileManagerApi';

export const downloadFile = async (baseUrl: string, item: FileItem, currentPath: string): Promise<void> => {
    const path = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`;

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
        const file = new File(Paths.document, item.name);
        await File.downloadFileAsync(`${baseUrl}${path}`, file);

        if (file.exists) {
            if (await Sharing.isAvailableAsync()) {
                await Sharing.shareAsync(file.uri);
            } else {
                console.log('Sharing not available');
            }
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
