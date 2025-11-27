import { FolderPlus, UploadCloud } from 'lucide-react-native';
import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

interface FileManagerToolbarProps {
    theme: any;
    onCreateFolder: () => void;
    onUpload: () => void;
}

export const FileManagerToolbar: React.FC<FileManagerToolbarProps> = ({
    theme,
    onCreateFolder,
    onUpload,
}) => {
    return (
        <View style={styles.toolbar}>
            <TouchableOpacity
                style={[styles.toolbarButton, { backgroundColor: theme.headerBg, borderColor: theme.border }]}
                onPress={onCreateFolder}
            >
                <FolderPlus size={18} color={theme.primary} />
                <Text style={[styles.toolbarText, { color: theme.text }]}>New Folder</Text>
            </TouchableOpacity>

            <TouchableOpacity
                style={[styles.toolbarButton, { backgroundColor: theme.headerBg, borderColor: theme.border }]}
                onPress={onUpload}
            >
                <UploadCloud size={18} color={theme.primary} />
                <Text style={[styles.toolbarText, { color: theme.text }]}>Upload</Text>
            </TouchableOpacity>
        </View>
    );
};

const styles = StyleSheet.create({
    toolbar: {
        flexDirection: 'row',
        paddingVertical: 12,
        paddingHorizontal: 16,
        gap: 12,
    },
    toolbarButton: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 8,
        paddingVertical: 8,
        paddingHorizontal: 16,
        borderRadius: 20,
        borderWidth: 1,
    },
    toolbarText: {
        fontSize: 13,
        fontWeight: '600',
    },
});
