import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Path, Svg } from 'react-native-svg';

interface FileManagerHeaderProps {
    theme: any;
    currentPath: string;
    insets: any;
    onBack: () => void;
    onRefresh: () => void;
}

const IconBack = ({ color = '#374151' }) => (
    <Svg width="20" height="20" fill="none" viewBox="0 0 24 24" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <Path d="M15 19l-7-7 7-7" />
    </Svg>
);

const IconRefresh = ({ color = '#374151' }) => (
    <Svg width="18" height="18" fill="none" viewBox="0 0 24 24" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <Path d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
    </Svg>
);

export const FileManagerHeader: React.FC<FileManagerHeaderProps> = ({
    theme,
    currentPath,
    insets,
    onBack,
    onRefresh,
}) => {
    return (
        <View style={[styles.header, {
            backgroundColor: theme.headerBg,
            borderBottomColor: theme.border,
            paddingTop: insets.top + 10
        }]}>

            {/* Path Display */}
            <View style={[styles.pathContainer, { backgroundColor: theme.contentBg, borderColor: theme.border }]}>
                <Text style={styles.folderIcon}>📂</Text>
                <Text style={[styles.pathText, { color: theme.text }]} numberOfLines={1}>
                    {currentPath}
                </Text>
            </View>

            {/* Actions */}
            <View style={styles.headerActions}>
                <TouchableOpacity onPress={onBack} style={styles.iconButton}>
                    <IconBack color={theme.text} />
                </TouchableOpacity>
                <TouchableOpacity onPress={onRefresh} style={styles.iconButton}>
                    <IconRefresh color={theme.text} />
                </TouchableOpacity>
            </View>
        </View>
    );
};

const styles = StyleSheet.create({
    header: {
        paddingBottom: 10,
        flexDirection: 'row',
        alignItems: 'center',
        paddingHorizontal: 12,
        borderBottomWidth: 1,
    },
    pathContainer: {
        flex: 1,
        flexDirection: 'row',
        alignItems: 'center',
        height: 30,
        borderRadius: 6,
        borderWidth: 1,
        paddingHorizontal: 8,
        marginRight: 12,
    },
    folderIcon: {
        fontSize: 14,
        marginRight: 6,
    },
    pathText: {
        fontSize: 13,
        fontWeight: '500',
    },
    headerActions: {
        flexDirection: 'row',
        gap: 8,
    },
    iconButton: {
        padding: 4,
    },
});
