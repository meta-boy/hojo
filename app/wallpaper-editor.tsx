import React from 'react';
import { useColorScheme } from 'react-native';
import { WallpaperEditor } from '../components/apps/wallpaper/WallpaperEditor';
import { Colors } from '../utils/theme';

export default function WallpaperEditorRoute() {
    const colorScheme = useColorScheme();
    const isDark = colorScheme === 'dark';
    const theme = isDark ? Colors.dark : Colors.light;

    return <WallpaperEditor theme={theme} />;
}
