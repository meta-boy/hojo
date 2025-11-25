import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Path, Svg } from 'react-native-svg';

interface FileManagerToolbarProps {
    theme: any;
    onCreateFolder: () => void;
    onUpload: () => void;
}

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

export const FileManagerToolbar: React.FC<FileManagerToolbarProps> = ({
    theme,
    onCreateFolder,
    onUpload,
}) => {
    return (
        <View style={[styles.toolbar, { borderBottomColor: theme.border }]}>
            <TouchableOpacity style={styles.toolbarButton} onPress={onCreateFolder}>
                <IconNewFolder color={theme.text} />
                <Text style={[styles.toolbarText, { color: theme.text }]}>New Folder</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.toolbarButton} onPress={onUpload}>
                <IconUpload color={theme.text} />
                <Text style={[styles.toolbarText, { color: theme.text }]}>Upload</Text>
            </TouchableOpacity>
        </View>
    );
};

const styles = StyleSheet.create({
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
    toolbarText: {
        fontSize: 13,
        fontWeight: '500',
    },
});
