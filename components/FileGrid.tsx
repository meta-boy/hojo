import { File, FileText, Folder, FolderOpen, Image } from 'lucide-react-native';
import React from 'react';
import { ActivityIndicator, Alert, Dimensions, FlatList, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { FileItem } from '../utils/fileManagerApi';

interface FileGridProps {
    theme: any;
    files: FileItem[];
    isLoading: boolean;
    refreshing: boolean;
    onRefresh: () => void;
    onNavigate: (item: FileItem) => void;
    onRename: (item: FileItem) => void;
    onDelete: (item: FileItem) => void;
    onDownload: (item: FileItem) => void;
}

const getFileIcon = (item: FileItem, color: string) => {
    if (item.type === 'dir') return <Folder size={32} color={color} fill={color + '20'} />;

    const ext = item.name.split('.').pop()?.toLowerCase();
    if (['jpg', 'jpeg', 'png', 'webp', 'bmp'].includes(ext || '')) {
        return <Image size={32} color={color} />;
    }
    if (['txt', 'md', 'json', 'log'].includes(ext || '')) {
        return <FileText size={32} color={color} />;
    }

    return <File size={32} color={color} />;
};

export const FileGrid: React.FC<FileGridProps> = ({
    theme,
    files,
    isLoading,
    refreshing,
    onRefresh,
    onNavigate,
    onRename,
    onDelete,
    onDownload,
}) => {
    const screenWidth = Dimensions.get('window').width;
    const numColumns = 4;
    const gap = 12;
    const padding = 16;
    const itemWidth = (screenWidth - (padding * 2) - (gap * (numColumns - 1))) / numColumns;

    const renderItem = ({ item }: { item: FileItem }) => (
        <TouchableOpacity
            style={[styles.gridItem, { width: itemWidth }]}
            onPress={() => onNavigate(item)}
            onLongPress={() => {
                Alert.alert(
                    'Options',
                    item.name,
                    [
                        { text: 'Rename', onPress: () => onRename(item) },
                        { text: 'Download', onPress: () => onDownload(item) },
                        { text: 'Delete', onPress: () => onDelete(item), style: 'destructive' },
                        { text: 'Cancel', style: 'cancel' },
                    ]
                );
            }}
        >
            <View style={[styles.iconContainer, { backgroundColor: theme.headerBg }]}>
                {getFileIcon(item, item.type === 'dir' ? theme.primary : theme.text)}
            </View>
            <Text style={[styles.gridItemName, { color: theme.subText }]} numberOfLines={2}>
                {item.name}
            </Text>
        </TouchableOpacity>
    );

    return (
        <View style={{ flex: 1 }}>
            <FlatList
                data={files}
                renderItem={renderItem}
                keyExtractor={(item) => item.name}
                numColumns={numColumns}
                contentContainerStyle={styles.gridContent}
                columnWrapperStyle={styles.columnWrapper}
                refreshing={refreshing}
                onRefresh={onRefresh}
                ListEmptyComponent={
                    !isLoading ? (
                        <View style={styles.emptyContainer}>
                            <FolderOpen size={48} color={theme.border} />
                            <Text style={[styles.emptyText, { color: theme.subText }]}>Folder is empty</Text>
                        </View>
                    ) : null
                }
            />

            {isLoading && !refreshing && (
                <View style={[styles.loadingOverlay, { backgroundColor: theme.windowBg }]}>
                    <ActivityIndicator size="large" color={theme.primary} />
                </View>
            )}
        </View>
    );
};

const styles = StyleSheet.create({
    gridContent: {
        padding: 16,
    },
    columnWrapper: {
        gap: 12,
        justifyContent: 'flex-start',
    },
    gridItem: {
        alignItems: 'center',
        marginBottom: 16,
    },
    iconContainer: {
        width: '100%',
        aspectRatio: 1,
        borderRadius: 16,
        alignItems: 'center',
        justifyContent: 'center',
        marginBottom: 8,
    },
    gridItemName: {
        fontSize: 11,
        textAlign: 'center',
        lineHeight: 14,
        fontWeight: '500',
    },
    emptyContainer: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
        paddingTop: 100,
        gap: 16,
    },
    emptyText: {
        fontSize: 16,
        fontWeight: '500',
    },
    loadingOverlay: {
        ...StyleSheet.absoluteFillObject,
        justifyContent: 'center',
        alignItems: 'center',
        opacity: 0.8,
    },
});