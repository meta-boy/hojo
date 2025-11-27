import Slider from '@react-native-community/slider';
import * as ImagePicker from 'expo-image-picker';
import { useRouter } from 'expo-router';
import { ArrowLeft, Camera, Image as ImageIcon } from 'lucide-react-native';
import React, { useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Platform, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import Svg, { Defs, FeColorMatrix, Filter, Image as SvgImage } from 'react-native-svg';
import ViewShot, { captureRef } from 'react-native-view-shot';
import { useEpaperConnectivity } from '../../../hooks/useEpaperConnectivity';
import EpaperConnectivity from '../../../modules/epaper-connectivity';
import { createFolder, fetchList, uploadFile } from '../../../utils/fileManagerApi';

interface WallpaperEditorProps {
    theme: any;
}

type Template = 'portrait' | 'landscape';

interface FilterState {
    grayscale: number;
    contrast: number;
    brightness: number;
    saturation: number;
}

const DEFAULT_FILTERS: FilterState = {
    grayscale: 0,
    contrast: 50,
    brightness: 100,
    saturation: 100,
};

const INK_SCREEN_PRESET: FilterState = {
    grayscale: 0,
    contrast: 130,
    brightness: 100,
    saturation: 50,
};

// Helper to generate color matrix
const generateColorMatrix = (filters: FilterState) => {
    const { grayscale, contrast, brightness, saturation } = filters;

    // 1. Grayscale (0-100) -> 0-1
    const g = grayscale / 100;
    const gInv = 1 - g;
    // Standard NTSC weights
    const rW = 0.299, gW = 0.587, bW = 0.114;

    const grayMatrix = [
        gInv + g * rW, g * gW, g * bW, 0, 0,
        g * rW, gInv + g * gW, g * bW, 0, 0,
        g * rW, g * gW, gInv + g * bW, 0, 0,
        0, 0, 0, 1, 0,
    ];

    // 2. Contrast (0-150) -> 0-3 (approx, centered at 50=1)
    // 50 -> 1.0
    // 0 -> 0.0
    // 150 -> 3.0
    const c = contrast / 50;
    const t = (1.0 - c) / 2.0;

    const contrastMatrix = [
        c, 0, 0, 0, t,
        0, c, 0, 0, t,
        0, 0, c, 0, t,
        0, 0, 0, 1, 0,
    ];

    // 3. Brightness (0-200) -> Offset -100 to +100 (approx)
    // 100 -> 0
    // Range needs to be small for SVG matrix (usually -1 to 1 or similar depending on implementation, but 0-255 is common for some)
    // For FeColorMatrix, values are typically 0-1 range or similar.
    // Let's try a smaller range. 100 is neutral.
    const b = (brightness - 100) / 100;

    const brightnessMatrix = [
        1, 0, 0, 0, b,
        0, 1, 0, 0, b,
        0, 0, 1, 0, b,
        0, 0, 0, 1, 0,
    ];

    // 4. Saturation (0-200) -> 0-2
    // 100 -> 1.0
    const s = saturation / 100;
    const sInv = 1 - s;
    const satMatrix = [
        sInv * rW + s, sInv * gW, sInv * bW, 0, 0,
        sInv * rW, sInv * gW + s, sInv * bW, 0, 0,
        sInv * rW, sInv * gW, sInv * bW + s, 0, 0,
        0, 0, 0, 1, 0,
    ];

    return { grayMatrix, contrastMatrix, brightnessMatrix, satMatrix };
};


export const WallpaperEditor: React.FC<WallpaperEditorProps> = ({ theme }) => {
    const router = useRouter();
    const [template, setTemplate] = useState<Template | null>(null);
    const [imageUri, setImageUri] = useState<string | null>(null);
    const [filters, setFilters] = useState<FilterState>(DEFAULT_FILTERS);
    const [saving, setSaving] = useState(false);
    const viewShotRef = useRef<View>(null);

    const { BASE_URL } = useEpaperConnectivity();

    const pickImage = async (useCamera: boolean) => {
        try {
            let result;
            const options: ImagePicker.ImagePickerOptions = {
                mediaTypes: ImagePicker.MediaTypeOptions.Images,
                allowsEditing: true,
                aspect: template === 'portrait' ? [3, 5] : [5, 3],
                quality: 1,
            };

            if (useCamera) {
                const perm = await ImagePicker.requestCameraPermissionsAsync();
                if (!perm.granted) {
                    Alert.alert('Permission needed', 'Camera permission is required.');
                    return;
                }
                result = await ImagePicker.launchCameraAsync(options);
            } else {
                const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
                if (!perm.granted) {
                    Alert.alert('Permission needed', 'Gallery permission is required.');
                    return;
                }
                result = await ImagePicker.launchImageLibraryAsync(options);
            }

            if (!result.canceled && result.assets[0]) {
                setImageUri(result.assets[0].uri);
            }
        } catch (error) {
            Alert.alert('Error', 'Failed to pick image');
        }
    };

    const handleSave = async () => {
        if (!imageUri) return;
        setSaving(true);
        try {
            // 1. Capture the filtered view
            const width = template === 'portrait' ? 480 : 800;
            const height = template === 'portrait' ? 800 : 480;

            const uri = await captureRef(viewShotRef, {
                format: 'jpg',
                quality: 0.9,
                width,
                height,
                result: 'tmpfile'
            });

            // 2. Generate filename
            const filename = `wallpaper_${Date.now()}.jpg`;

            // 3. Ensure connection to E-Paper
            if (Platform.OS === 'android') {
                await EpaperConnectivity.bindToEpaperNetwork();
                // Give it a moment to stabilize
                await new Promise(resolve => setTimeout(resolve, 1000));
            }

            // 4. Check/Create /backgrounds folder
            const rootFiles = await fetchList(BASE_URL, '/');
            const bgFolderExists = rootFiles.some((f: any) => f.name === 'backgrounds' && f.type === 'dir');

            if (!bgFolderExists) {
                await createFolder(BASE_URL, '/backgrounds');
            }

            // 5. Upload
            const targetPath = `/backgrounds/${filename}`;
            await uploadFile(BASE_URL, uri, filename, targetPath);

            Alert.alert('Success', 'Wallpaper saved and pushed to /backgrounds');

        } catch (error) {
            console.error(error);
            Alert.alert('Error', 'Failed to save wallpaper');
            // Ensure we re-bind if we failed 
            if (Platform.OS === 'android') {
                EpaperConnectivity.bindToEpaperNetwork().catch(console.error);
            }
        } finally {
            setSaving(false);
        }
    };

    const matrices = useMemo(() => generateColorMatrix(filters), [filters]);

    if (!template) {
        return (
            <View style={[styles.container, { backgroundColor: theme.windowBg }]}>
                 {/* Header for consistency */}
                 <View style={[styles.header, { borderBottomWidth: 0 }]}>
                    <View style={{ width: 24 }} />
                    <Text style={[styles.headerTitle, { color: theme.text }]}>Wallpaper</Text>
                    <View style={{ width: 24 }} />
                </View>

                <View style={styles.centerContent}>
                    <Text style={[styles.sectionTitle, { color: theme.subText }]}>Select Orientation</Text>
                    <View style={styles.templateContainer}>
                        <TouchableOpacity
                            style={[styles.templateBtn, { borderColor: theme.border, backgroundColor: theme.headerBg }]}
                            onPress={() => setTemplate('portrait')}
                        >
                            <View style={[styles.aspectRatioBox, { width: 60, height: 100, backgroundColor: theme.primary }]} />
                            <Text style={[styles.templateText, { color: theme.text }]}>Portrait</Text>
                            <Text style={[styles.templateSubText, { color: theme.subText }]}>3:5</Text>
                        </TouchableOpacity>
                        <TouchableOpacity
                            style={[styles.templateBtn, { borderColor: theme.border, backgroundColor: theme.headerBg }]}
                            onPress={() => setTemplate('landscape')}
                        >
                            <View style={[styles.aspectRatioBox, { width: 100, height: 60, backgroundColor: theme.primary }]} />
                            <Text style={[styles.templateText, { color: theme.text }]}>Landscape</Text>
                             <Text style={[styles.templateSubText, { color: theme.subText }]}>5:3</Text>
                        </TouchableOpacity>
                    </View>
                </View>
            </View>
        );
    }

    if (!imageUri) {
        return (
            <View style={[styles.container, { backgroundColor: theme.windowBg }]}>
                <View style={[styles.header, { borderBottomColor: theme.border }]}>
                    <TouchableOpacity onPress={() => setTemplate(null)}>
                        <ArrowLeft size={24} color={theme.text} />
                    </TouchableOpacity>
                    <Text style={[styles.headerTitle, { color: theme.text }]}>Select Image</Text>
                    <View style={{ width: 24 }} />
                </View>
                <View style={styles.centerContent}>
                    <TouchableOpacity
                        style={[styles.actionBtn, { backgroundColor: theme.primary }]}
                        onPress={() => pickImage(true)}
                    >
                        <Camera size={24} color="#FFF" />
                        <Text style={styles.actionBtnText}>Take Photo</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                        style={[styles.actionBtn, { backgroundColor: theme.headerBg, borderColor: theme.border, borderWidth: 1, marginTop: 16 }]}
                        onPress={() => pickImage(false)}
                    >
                        <ImageIcon size={24} color={theme.text} />
                        <Text style={[styles.actionBtnText, { color: theme.text }]}>From Album</Text>
                    </TouchableOpacity>
                </View>
            </View>
        );
    }

    return (
        <View style={[styles.container, { backgroundColor: theme.windowBg }]}>
            <View style={[styles.header, { borderBottomColor: theme.border }]}>
                <TouchableOpacity onPress={() => setImageUri(null)}>
                    <ArrowLeft size={24} color={theme.text} />
                </TouchableOpacity>
                <Text style={[styles.headerTitle, { color: theme.text }]}>Edit</Text>
                <TouchableOpacity onPress={handleSave} disabled={saving}>
                    {saving ? <ActivityIndicator color={theme.primary} /> : <Text style={{ color: theme.primary, fontWeight: 'bold', fontSize: 16 }}>Save</Text>}
                </TouchableOpacity>
            </View>

            <ScrollView contentContainerStyle={styles.editorContent}>
                <View style={styles.previewContainer}>
                    <ViewShot ref={viewShotRef} options={{ format: 'jpg', quality: 0.9 }}>
                        <Svg width={300} height={template === 'portrait' ? 500 : 180} viewBox={template === 'portrait' ? "0 0 300 500" : "0 0 300 180"}>
                            <Defs>
                                <Filter id="filters">
                                    <FeColorMatrix type="matrix" values={matrices.satMatrix.join(' ')} result="saturated" />
                                    <FeColorMatrix in="saturated" type="matrix" values={matrices.brightnessMatrix.join(' ')} result="bright" />
                                    <FeColorMatrix in="bright" type="matrix" values={matrices.contrastMatrix.join(' ')} result="contrasted" />
                                    <FeColorMatrix in="contrasted" type="matrix" values={matrices.grayMatrix.join(' ')} />
                                </Filter>
                            </Defs>
                            <SvgImage
                                x="0"
                                y="0"
                                width="100%"
                                height="100%"
                                preserveAspectRatio="xMidYMid meet"
                                href={{ uri: imageUri }}
                                filter="url(#filters)"
                            />
                        </Svg>
                    </ViewShot>
                </View>

                <View style={[styles.controls, { backgroundColor: theme.contentBg }]}>
                    <View style={styles.presetRow}>
                        <TouchableOpacity onPress={() => setFilters(DEFAULT_FILTERS)}>
                            <Text style={{ color: theme.primary }}>Reset</Text>
                        </TouchableOpacity>
                        <TouchableOpacity
                            style={[styles.presetBtn, { backgroundColor: theme.primary }]}
                            onPress={() => setFilters(INK_SCREEN_PRESET)}
                        >
                            <Text style={{ color: '#FFF' }}>Ink Screen</Text>
                        </TouchableOpacity>
                    </View>

                    <FilterSlider
                        label="Grayscale"
                        value={filters.grayscale}
                        min={0} max={100}
                        onChange={(v: number) => setFilters(prev => ({ ...prev, grayscale: v }))}
                        theme={theme}
                    />
                    <FilterSlider
                        label="Contrast"
                        value={filters.contrast}
                        min={0} max={150}
                        onChange={(v: number) => setFilters(prev => ({ ...prev, contrast: v }))}
                        theme={theme}
                    />
                    <FilterSlider
                        label="Brightness"
                        value={filters.brightness}
                        min={0} max={200}
                        onChange={(v: number) => setFilters(prev => ({ ...prev, brightness: v }))}
                        theme={theme}
                    />
                    <FilterSlider
                        label="Saturation"
                        value={filters.saturation}
                        min={0} max={200}
                        onChange={(v: number) => setFilters(prev => ({ ...prev, saturation: v }))}
                        theme={theme}
                    />
                </View>
            </ScrollView>
        </View>
    );
};

const FilterSlider = ({ label, value, min, max, onChange, theme }: any) => (
    <View style={styles.sliderContainer}>
        <View style={styles.sliderHeader}>
            <Text style={[styles.sliderLabel, { color: theme.text }]}>{label}</Text>
            <Text style={[styles.sliderValue, { color: theme.subText }]}>{Math.round(value)}</Text>
        </View>
        <Slider
            style={{ width: '100%', height: 40 }}
            minimumValue={min}
            maximumValue={max}
            value={value}
            onValueChange={onChange}
            minimumTrackTintColor={theme.primary}
            maximumTrackTintColor={theme.border}
            thumbTintColor={theme.primary}
        />
    </View>
);

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    sectionTitle: {
        fontSize: 16,
        fontWeight: '600',
        marginBottom: 24,
    },
    templateContainer: {
        flexDirection: 'row',
        justifyContent: 'center',
        gap: 20,
    },
    templateBtn: {
        padding: 24,
        borderRadius: 24,
        borderWidth: 1,
        alignItems: 'center',
        minWidth: 120,
    },
    aspectRatioBox: {
        marginBottom: 16,
        borderRadius: 8,
    },
    templateText: {
        fontWeight: '600',
        fontSize: 16,
        marginBottom: 2,
    },
    templateSubText: {
        fontSize: 13,
    },
    header: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: 16,
        borderBottomWidth: 1,
        borderBottomColor: 'rgba(0,0,0,0.1)',
    },
    headerTitle: {
        fontSize: 18,
        fontWeight: 'bold',
    },
    centerContent: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
        padding: 20,
    },
    actionBtn: {
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: 12,
        paddingHorizontal: 24,
        borderRadius: 8,
        gap: 10,
        width: 200,
        justifyContent: 'center',
    },
    actionBtnText: {
        color: '#FFF',
        fontWeight: '600',
        fontSize: 16,
    },
    editorContent: {
        padding: 16,
        alignItems: 'center',
    },
    previewContainer: {
        marginBottom: 20,
        borderWidth: 1,
        borderColor: '#ddd',
        elevation: 5,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.2,
        shadowRadius: 4,
    },
    controls: {
        width: '100%',
        padding: 16,
        borderRadius: 12,
    },
    presetRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 20,
    },
    presetBtn: {
        paddingVertical: 6,
        paddingHorizontal: 12,
        borderRadius: 16,
    },
    sliderContainer: {
        marginBottom: 16,
    },
    sliderHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        marginBottom: 4,
    },
    sliderLabel: {
        fontSize: 14,
        fontWeight: '500',
    },
    sliderValue: {
        fontSize: 12,
    },
});
