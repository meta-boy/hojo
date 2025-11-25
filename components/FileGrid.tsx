import React from 'react';
import { ActivityIndicator, Alert, FlatList, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
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
}

export const FileGrid: React.FC<FileGridProps> = ({
    theme,
    files,
    isLoading,
    refreshing,
    onRefresh,
    onNavigate,
    onRename,
    onDelete,
}) => {
    const renderItem = ({ item }: { item: FileItem }) => (
        <TouchableOpacity
            style={[styles.gridItem, { borderColor: 'transparent' }]}
            onPress={() => onNavigate(item)}
            onLongPress={() => {
                Alert.alert(
                    'Options',
                    item.name,
                    [
                        { text: 'Rename', onPress: () => onRename(item) },
                        { text: 'Delete', onPress: () => onDelete(item), style: 'destructive' },
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

    return (
        <View style={{ flex: 1 }}>
            <FlatList
                data={files}
                renderItem={renderItem}
                keyExtractor={(item) => item.name}
                numColumns={4} // Grid layout
                contentContainerStyle={styles.gridContent}
                columnWrapperStyle={styles.columnWrapper}
                refreshing={refreshing}
                onRefresh={onRefresh}
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
    );
};

const styles = StyleSheet.create({
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
    loadingOverlay: {
        ...StyleSheet.absoluteFillObject,
        backgroundColor: 'rgba(255,255,255,0.5)',
        justifyContent: 'center',
        alignItems: 'center',
    },
});
