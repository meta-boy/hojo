import { Stack } from 'expo-router';
import React from 'react';
import { StatusBar, StyleSheet, useColorScheme, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { FileGrid } from '../components/FileGrid';
import { FileManagerHeader } from '../components/FileManagerHeader';
import { FileManagerToolbar } from '../components/FileManagerToolbar';
import { InputModal } from '../components/InputModal';
import { useFileManager } from '../hooks/useFileManager';

const BASE_URL = 'http://192.168.3.3';

// macOS Theme Colors
const Colors = {
    light: {
        windowBg: '#F3F4F6', // gray-100
        headerBg: '#E5E7EB', // gray-200
        sidebarBg: '#F9FAFB', // gray-50
        contentBg: '#FFFFFF',
        text: '#374151', // gray-700
        subText: '#9CA3AF', // gray-400
        border: '#D1D5DB', // gray-300
        selection: '#E4EFFD',
        selectionBorder: '#BFDBFE',
        primary: '#3B82F6', // blue-500
        danger: '#EF4444', // red-500
    },
    dark: {
        windowBg: '#1F2937', // gray-800
        headerBg: '#111827', // gray-900
        sidebarBg: '#1F2937',
        contentBg: '#111827',
        text: '#E5E7EB', // gray-200
        subText: '#6B7280', // gray-500
        border: '#374151', // gray-700
        selection: '#1E3A8A', // blue-900
        selectionBorder: '#1D4ED8',
        primary: '#60A5FA', // blue-400
        danger: '#F87171', // red-400
    },
};

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
        handleModalSubmit
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
