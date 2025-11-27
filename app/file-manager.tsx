import { Stack } from 'expo-router';
import React from 'react';
import { StatusBar, StyleSheet, useColorScheme, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { FileGrid } from '../components/FileGrid';
import { FileManagerHeader } from '../components/FileManagerHeader';
import { FileManagerToolbar } from '../components/FileManagerToolbar';
import { InputModal } from '../components/InputModal';
import { useFileManager } from '../hooks/useFileManager';
import { Colors } from '../utils/theme';

const BASE_URL = 'http://192.168.3.3';

export default function FileManager() {
    const insets = useSafeAreaInsets();
    const colorScheme = useColorScheme();
    const isDark = colorScheme === 'dark';
    const theme = isDark ? Colors.dark : Colors.light;

    const {
        currentPath,
        files,
        isLoading,
        refreshing,
        modalVisible,
        setModalVisible,
        modalMode,
        inputText,
        setInputText,
        handleRefresh,
        handleNavigate,
        handleGoBack,
        handleCreateFolder,
        handleRename,
        handleDelete,
        handleUpload,
        handleModalSubmit,
        handleDownload
    } = useFileManager(BASE_URL);

    return (
        <View style={[styles.container, { backgroundColor: theme.windowBg }]}>
            <Stack.Screen options={{ headerShown: false }} />
            <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} translucent backgroundColor="transparent" />

            {/* Main Window Container */}
            <View style={[styles.window, { backgroundColor: theme.contentBg }]}>

                {/* Window Header */}
                <FileManagerHeader
                    theme={theme}
                    currentPath={currentPath}
                    insets={insets}
                    onBack={handleGoBack}
                    onRefresh={handleRefresh}
                />

                {/* Toolbar (Secondary Header) */}
                <FileManagerToolbar
                    theme={theme}
                    onCreateFolder={handleCreateFolder}
                    onUpload={handleUpload}
                />

                {/* File Grid */}
                <FileGrid
                    theme={theme}
                    files={files}
                    isLoading={isLoading}
                    refreshing={refreshing}
                    onRefresh={handleRefresh}
                    onNavigate={handleNavigate}
                    onRename={handleRename}
                    onDelete={handleDelete}
                    onDownload={handleDownload}
                />

            </View>

            {/* Input Modal */}
            <InputModal
                visible={modalVisible}
                theme={theme}
                title={modalMode === 'create' ? 'New Folder' : 'Rename Item'}
                value={inputText}
                placeholder={modalMode === 'create' ? "Folder Name" : "New Name"}
                submitLabel={modalMode === 'create' ? 'Create' : 'Rename'}
                onClose={() => setModalVisible(false)}
                onChangeText={setInputText}
                onSubmit={handleModalSubmit}
            />
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    window: {
        flex: 1,
    },
});
