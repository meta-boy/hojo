import { ChevronLeft, FolderOpen, RotateCw } from 'lucide-react-native';
import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

interface FileManagerHeaderProps {
    theme: any;
    currentPath: string;
    insets: any;
    onBack: () => void;
    onRefresh: () => void;
}

export const FileManagerHeader: React.FC<FileManagerHeaderProps> = ({
    theme,
    currentPath,
    insets,
    onBack,
    onRefresh,
}) => {
    return (
        <View style={[styles.header, {
            backgroundColor: theme.contentBg,
            paddingTop: insets.top + 20
        }]}>

            {/* Actions */}
            <View style={styles.headerActions}>
                <TouchableOpacity onPress={onBack} style={styles.iconButton}>
                    <ChevronLeft size={24} color={theme.text} />
                </TouchableOpacity>
            </View>

            {/* Path Display */}
            <View style={[styles.pathContainer, { backgroundColor: theme.headerBg }]}>
                <FolderOpen size={14} color={theme.subText} style={{ marginRight: 6 }} />
                <Text style={[styles.pathText, { color: theme.subText }]} numberOfLines={1}>
                    {currentPath}
                </Text>
            </View>

            <TouchableOpacity onPress={onRefresh} style={styles.iconButton}>
                <RotateCw size={20} color={theme.text} />
            </TouchableOpacity>

        </View>
    );
};

const styles = StyleSheet.create({
    header: {
        paddingBottom: 10,
        flexDirection: 'row',
        alignItems: 'center',
        paddingHorizontal: 16,
        justifyContent: 'space-between',
    },
    pathContainer: {
        flex: 1,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        height: 32,
        borderRadius: 16,
        paddingHorizontal: 12,
        marginHorizontal: 12,
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
