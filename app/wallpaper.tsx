import { useConfig } from '@/components/ConfigContext';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { uploadFile } from '@/utils/fileManagerApi';
import { Ionicons } from '@expo/vector-icons';
import * as FileSystem from 'expo-file-system/legacy';
import * as ImageManipulator from 'expo-image-manipulator';
import * as ImagePicker from 'expo-image-picker';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import jpeg from 'jpeg-js';
import React, { useState } from 'react';
import { ActivityIndicator, Alert, Image, StyleSheet, TouchableOpacity, View, useColorScheme } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

// RGB565 Conversion Helper
// We will need a way to get raw pixels. Since we don't have a native pixel accessor easily,
// we might have to rely on a JS decoder or just saving the image for now.
// For this step, I will implement the UI and the resizing.
// I will add a placeholder for the binary conversion.

// Helper to convert Base64 to Uint8Array
const toUint8Array = (base64: string) => {
    const binaryString = atob(base64);
    const len = binaryString.length;
    const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) {
        bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes;
};

// Helper to convert Uint8Array to Base64
const fromUint8Array = (bytes: Uint8Array) => {
    let binary = '';
    const len = bytes.byteLength;
    for (let i = 0; i < len; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
};

export default function WallpaperScreen() {
    const router = useRouter();
    const { path } = useLocalSearchParams<{ path: string }>();
    const currentPath = path || '/';

    const insets = useSafeAreaInsets();
    const colorScheme = useColorScheme();
    const { baseUrl } = useConfig();
    const isDark = colorScheme === 'dark';

    const [imageUri, setImageUri] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);
    const [processing, setProcessing] = useState(false);

    const pickImage = async () => {
        try {
            const result = await ImagePicker.launchImageLibraryAsync({
                mediaTypes: ImagePicker.MediaTypeOptions.Images,
                allowsEditing: true,
                aspect: [3, 5], // 480:800
                quality: 1,
            });

            if (!result.canceled) {
                setImageUri(result.assets[0].uri);
            }
        } catch (e) {
            Alert.alert('Error', 'Failed to pick image');
        }
    };

    const processAndSave = async () => {
        if (!imageUri) return;
        setProcessing(true);

        try {
            // 1. Resize to exactly 480x800
            // We use JPEG format to ensure we can decode it with jpeg-js
            const manipulated = await ImageManipulator.manipulateAsync(
                imageUri,
                [{ resize: { width: 480, height: 800 } }],
                { format: ImageManipulator.SaveFormat.JPEG, base64: true, compress: 1 }
            );

            if (!manipulated.base64) throw new Error('No base64 data');

            // 2. Decode JPEG to Raw Pixels (RGBA)
            const jpegData = toUint8Array(manipulated.base64);
            const rawImageData = jpeg.decode(jpegData, { useTArray: true }); // returns { width, height, data: Uint8Array }

            // 3. Convert RGBA to BMP (24-bit RGB)
            const { width, height, data } = rawImageData;
            const rowSize = width * 3;
            const padding = (4 - (rowSize % 4)) % 4; // Should be 0 for 480 width
            const fileSize = 14 + 40 + (rowSize + padding) * height;

            const bmpData = new Uint8Array(fileSize);
            const view = new DataView(bmpData.buffer);

            // BMP Header (14 bytes)
            view.setUint16(0, 0x4D42, true); // 'BM'
            view.setUint32(2, fileSize, true); // File size
            view.setUint32(6, 0, true); // Reserved
            view.setUint32(10, 54, true); // Offset to pixel data (14 + 40)

            // DIB Header (40 bytes)
            view.setUint32(14, 40, true); // Header size
            view.setInt32(18, width, true); // Width
            view.setInt32(22, height, true); // Height (positive = bottom-up)
            view.setUint16(26, 1, true); // Planes
            view.setUint16(28, 24, true); // BPP (24-bit)
            view.setUint32(30, 0, true); // Compression (BI_RGB)
            view.setUint32(34, (rowSize + padding) * height, true); // Image size
            view.setInt32(38, 0, true); // Xppm
            view.setInt32(42, 0, true); // Yppm
            view.setUint32(46, 0, true); // Colors used
            view.setUint32(50, 0, true); // Important colors

            // Pixel Data (Bottom-Up, BGR)
            let offset = 54;
            for (let y = height - 1; y >= 0; y--) {
                for (let x = 0; x < width; x++) {
                    const i = (y * width + x) * 4;
                    const r = data[i];
                    const g = data[i + 1];
                    const b = data[i + 2];

                    // BGR
                    bmpData[offset++] = b;
                    bmpData[offset++] = g;
                    bmpData[offset++] = r;
                }
                // Padding
                for (let p = 0; p < padding; p++) {
                    bmpData[offset++] = 0;
                }
            }

            // 4. Save to file system
            const base64Output = fromUint8Array(bmpData);
            const fileName = `wallpaper_${Date.now()}.bmp`;
            const tempFile = FileSystem.cacheDirectory + fileName;

            await FileSystem.writeAsStringAsync(tempFile, base64Output, {
                encoding: FileSystem.EncodingType.Base64,
            });

            // 5. Upload to current path
            const targetPath = currentPath === '/' ? `/${fileName}` : `${currentPath}/${fileName}`;
            await uploadFile(baseUrl, tempFile, fileName, targetPath);

            Alert.alert('Success', 'Wallpaper saved to ' + (currentPath === '/' ? 'root' : currentPath));
            router.back();

        } catch (e) {
            console.error(e);
            Alert.alert('Error', 'Failed to process wallpaper');
        } finally {
            setProcessing(false);
        }
    };

    return (
        <ThemedView style={[styles.container, { paddingTop: insets.top }]}>
            <Stack.Screen options={{ headerShown: false }} />

            <View style={styles.header}>
                <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
                    <Ionicons name="arrow-back" size={24} color={isDark ? '#fff' : '#000'} />
                </TouchableOpacity>
                <ThemedText style={styles.title}>Wallpaper Maker</ThemedText>
            </View>

            <View style={styles.content}>
                <View style={styles.previewContainer}>
                    {imageUri ? (
                        <Image source={{ uri: imageUri }} style={styles.preview} resizeMode="contain" />
                    ) : (
                        <View style={[styles.placeholder, { borderColor: isDark ? '#374151' : '#E5E7EB' }]}>
                            <Ionicons name="image-outline" size={48} color={isDark ? '#6B7280' : '#9CA3AF'} />
                            <ThemedText style={styles.placeholderText}>Select an image</ThemedText>
                        </View>
                    )}
                </View>

                <View style={styles.actions}>
                    <TouchableOpacity style={[styles.button, styles.pickButton]} onPress={pickImage}>
                        <Ionicons name="images" size={20} color="#fff" />
                        <ThemedText style={styles.buttonText}>Pick Image</ThemedText>
                    </TouchableOpacity>

                    {imageUri && (
                        <TouchableOpacity
                            style={[styles.button, styles.saveButton, processing && styles.disabledButton]}
                            onPress={processAndSave}
                            disabled={processing}
                        >
                            {processing ? (
                                <ActivityIndicator color="#fff" />
                            ) : (
                                <>
                                    <Ionicons name="save" size={20} color="#fff" />
                                    <ThemedText style={styles.buttonText}>Save as .bmp</ThemedText>
                                </>
                            )}
                        </TouchableOpacity>
                    )}
                </View>
            </View>
        </ThemedView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1 },
    header: {
        flexDirection: 'row', alignItems: 'center', padding: 16,
        borderBottomWidth: 1, borderBottomColor: 'rgba(0,0,0,0.1)',
    },
    backButton: { marginRight: 16 },
    title: { fontSize: 20, fontWeight: 'bold' },
    content: { flex: 1, padding: 20, alignItems: 'center', justifyContent: 'center' },
    previewContainer: {
        width: 240, height: 400, // 3:5 aspect ratio preview
        marginBottom: 40,
        justifyContent: 'center', alignItems: 'center',
    },
    preview: { width: '100%', height: '100%', borderRadius: 12 },
    placeholder: {
        width: '100%', height: '100%', borderRadius: 12, borderWidth: 2, borderStyle: 'dashed',
        justifyContent: 'center', alignItems: 'center',
    },
    placeholderText: { marginTop: 12, color: '#6B7280' },
    actions: { width: '100%', gap: 16 },
    button: {
        flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8,
        padding: 16, borderRadius: 12,
    },
    pickButton: { backgroundColor: '#3B82F6' },
    saveButton: { backgroundColor: '#10B981' },
    disabledButton: { opacity: 0.7 },
    buttonText: { color: '#fff', fontWeight: '600', fontSize: 16 },
});
