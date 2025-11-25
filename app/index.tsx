import { Stack, useRouter } from 'expo-router';
import React from 'react';
import { Alert, ScrollView, StatusBar, StyleSheet, useColorScheme, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { AppDock } from '../components/AppDock';
import { QuickLinkModal } from '../components/QuickLinkModal';
import { StatusWidget } from '../components/StatusWidget';
import { useEpaperConnectivity } from '../hooks/useEpaperConnectivity';
import { useQuickLink } from '../hooks/useQuickLink';

// macOS Theme Colors
const Colors = {
  light: {
    windowBg: '#F3F4F6', // gray-100
    headerBg: '#E5E7EB', // gray-200
    contentBg: '#FFFFFF',
    text: '#374151', // gray-700
    subText: '#9CA3AF', // gray-400
    border: '#D1D5DB', // gray-300
    primary: '#3B82F6', // blue-500
    success: '#22C55E',
    error: '#EF4444',
  },
  dark: {
    windowBg: '#1F2937', // gray-800
    headerBg: '#111827', // gray-900
    contentBg: '#111827',
    text: '#E5E7EB', // gray-200
    subText: '#6B7280', // gray-500
    border: '#374151', // gray-700
    primary: '#60A5FA', // blue-400
    success: '#22C55E',
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

        {/* Window Header */}
        <View style={[styles.header, {
          backgroundColor: theme.headerBg,
          borderBottomColor: theme.border,
          paddingTop: insets.top + 10
        }]}>
        </View>

        <ScrollView contentContainerStyle={styles.content}>

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
  header: {
    paddingBottom: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 12,
    borderBottomWidth: 1,
  },
  content: {
    padding: 20,
  },
});
