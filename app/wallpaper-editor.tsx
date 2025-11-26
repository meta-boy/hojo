import React from 'react';
import { useColorScheme } from 'react-native';
import { WallpaperEditor } from '../components/apps/wallpaper/WallpaperEditor';

// Reuse the theme colors from app/index.tsx or a shared theme file
// For now, I'll duplicate the theme logic to ensure consistency
const Colors = {
    light: {
        windowBg: '#F3F4F6',
        headerBg: '#E5E7EB',
        contentBg: '#FFFFFF',
        text: '#374151',
        subText: '#9CA3AF',
        border: '#D1D5DB',
        primary: '#3B82F6',
        success: '#22C55E',
        error: '#EF4444',
    },
    dark: {
        windowBg: '#1F2937',
        headerBg: '#111827',
        contentBg: '#111827',
        text: '#E5E7EB',
        subText: '#6B7280',
        border: '#374151',
        primary: '#60A5FA',
        success: '#22C55E',
        error: '#EF4444',
    },
};

export default function WallpaperEditorRoute() {
    const colorScheme = useColorScheme();
    const isDark = colorScheme === 'dark';
    const theme = isDark ? Colors.dark : Colors.light;

    return <WallpaperEditor theme={theme} />;
}
