import { Readability } from '@mozilla/readability';
import * as FileSystem from 'expo-file-system/legacy';
import { parseHTML } from 'linkedom';
import { useState } from 'react';
import { Alert, Platform } from 'react-native';
import EpaperConnectivity from '../modules/epaper-connectivity';
import { createFolder, fetchList, uploadFile } from '../utils/fileManagerApi';

export function useQuickLink(BASE_URL: string) {
    const [quickLinkVisible, setQuickLinkVisible] = useState(false);
    const [quickLinkUrl, setQuickLinkUrl] = useState('');
    const [converting, setConverting] = useState(false);

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

    return {
        quickLinkVisible,
        setQuickLinkVisible,
        quickLinkUrl,
        setQuickLinkUrl,
        converting,
        handleConvertAndUpload
    };
}
