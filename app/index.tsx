import { Stack, useRouter } from 'expo-router';
import React from 'react';
import { Alert, ScrollView, StatusBar, StyleSheet, Text, useColorScheme, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { AppDock } from '../components/AppDock';
import { QuickLinkModal } from '../components/QuickLinkModal';
import { StatusWidget } from '../components/StatusWidget';
import { useEpaperConnectivity } from '../hooks/useEpaperConnectivity';
import { useQuickLink } from '../hooks/useQuickLink';

// Dark Minimal Theme Colors
const Colors = {
  light: {
    windowBg: '#F3F4F6', // gray-100
    headerBg: '#E5E7EB', // gray-200
    contentBg: '#FFFFFF',
    text: '#374151', // gray-700
    subText: '#9CA3AF', // gray-400
    border: '#D1D5DB', // gray-300
    primary: '#10B981', // emerald-500
    success: '#10B981',
    error: '#EF4444',
  },
  dark: {
    windowBg: '#09090b', // zinc-950 (Very dark background)
    headerBg: '#18181b', // zinc-900
    contentBg: '#09090b', // zinc-950
    text: '#F4F4F5', // zinc-100 (High contrast text)
    subText: '#A1A1AA', // zinc-400
    border: '#27272A', // zinc-800 (Subtle border)
    primary: '#10B981', // emerald-500 (Neon/Vibrant Green Accent)
    success: '#10B981',
    error: '#EF4444',
  },
};

export default function App() {
  const colorScheme = useColorScheme();
  const isDark = colorScheme === 'dark';
  const theme = isDark ? Colors.dark : Colors.light;
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const {
    isConnected,
    isConnecting,
    storageStatus,
    handleConnect,
    BASE_URL
  } = useEpaperConnectivity();

  const {
    quickLinkVisible,
    setQuickLinkVisible,
    quickLinkUrl,
    setQuickLinkUrl,
    converting,
    handleConvertAndUpload
  } = useQuickLink(BASE_URL);

  const handleAction = (action: string) => {
    if (action === 'File Manager') {
      router.push('/file-manager');
    } else if (action === 'Quick Link') {
      setQuickLinkVisible(true);
    } else if (action === 'Wallpaper Editor') {
      router.push('/wallpaper-editor');
    } else {
      Alert.alert('Action', `Clicked: ${action}`);
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.windowBg }]}>
      <Stack.Screen options={{ headerShown: false }} />
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} translucent backgroundColor="transparent" />

      {/* Main Window */}
      <View style={[styles.window, { backgroundColor: theme.contentBg }]}>

        {/* Header Spacer */}
        <View style={{ height: insets.top }} />

        <ScrollView
            contentContainerStyle={styles.content}
            showsVerticalScrollIndicator={false}
        >
             {/* Header Title */}
             <View style={styles.titleContainer}>
                <Text style={[styles.headerTitle, { color: theme.text }]}>Dashboard</Text>
                <Text style={[styles.headerSubtitle, { color: theme.subText }]}>Manage your device</Text>
             </View>

          {/* Status Widget */}
          <StatusWidget
            theme={theme}
            isConnected={isConnected}
            isConnecting={isConnecting}
            storageStatus={storageStatus}
            onConnect={handleConnect}
          />

          {/* Dock / Actions Grid */}
          <AppDock
            theme={theme}
            onAction={handleAction}
          />

        </ScrollView>
      </View>

      {/* Quick Link Modal */}
      <QuickLinkModal
        visible={quickLinkVisible}
        theme={theme}
        url={quickLinkUrl}
        converting={converting}
        onClose={() => setQuickLinkVisible(false)}
        onChangeUrl={setQuickLinkUrl}
        onSubmit={handleConvertAndUpload}
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
  titleContainer: {
    paddingHorizontal: 4,
    marginBottom: 24,
    marginTop: 20,
  },
  headerTitle: {
    fontSize: 32,
    fontWeight: '800',
    letterSpacing: -0.5,
  },
  headerSubtitle: {
    fontSize: 16,
    marginTop: 4,
    fontWeight: '500',
  },
  content: {
    padding: 24,
  },
});
