import { useEffect, useRef, useState } from 'react';
import { Alert, AppState, Platform } from 'react-native';
import EpaperConnectivity from '../modules/epaper-connectivity';
import { fetchStatus, StorageStatus } from '../utils/fileManagerApi';

const BASE_URL = 'http://192.168.3.3';

export function useEpaperConnectivity() {
    const [isConnected, setIsConnected] = useState(false);
    const [isBound, setIsBound] = useState(false);
    const [isConnecting, setIsConnecting] = useState(false);
    const [storageStatus, setStorageStatus] = useState<StorageStatus | null>(null);
    const appState = useRef(AppState.currentState);

    useEffect(() => {
        const subscription = AppState.addEventListener('change', nextAppState => {
            if (appState.current.match(/inactive|background/) && nextAppState === 'active') {
                checkConnection();
            }
            appState.current = nextAppState;
        });

        checkConnection();
        const interval = setInterval(() => {
            if (!isConnected && !isConnecting) {
                handleConnect(true);
            }
        }, 5000);

        return () => {
            subscription.remove();
            clearInterval(interval);
        };
    }, [isConnected, isConnecting]);

    useEffect(() => {
        if (isConnected) {
            updateDeviceStatus();
        }
    }, [isConnected]);

    const checkConnection = async () => {
        try {
            const connected = await EpaperConnectivity.isConnected();
            setIsConnected(connected);
        } catch (error) {
            console.error('Connection check failed:', error);
        }
    };

    const updateDeviceStatus = async () => {
        try {
            if (Platform.OS === 'android' && !isBound) {
                await EpaperConnectivity.bindToEpaperNetwork();
                setIsBound(true);
                // Wait a moment for network binding to stabilize
                await new Promise(resolve => setTimeout(resolve, 1000));
            }

            const status = await fetchStatus(BASE_URL);
            setStorageStatus(status);
        } catch (error) {
            console.warn('Failed to fetch device status:', error);
        }
    };

    const handleConnect = async (silent = false) => {
        if (isConnecting) return;
        setIsConnecting(true);
        try {
            const success = await EpaperConnectivity.connectToEpaperHotspot();
            if (success) {
                setIsConnected(true);
                if (!silent) Alert.alert('Success', 'Connected to E-Paper');

                // Bind network immediately after connection
                if (Platform.OS === 'android') {
                    await EpaperConnectivity.bindToEpaperNetwork();
                    setIsBound(true);
                }

                // Fetch status after a short delay to allow network to stabilize
                setTimeout(() => {
                    updateDeviceStatus();
                }, 1000);
            }
        } catch (error: any) {
            if (!silent) Alert.alert('Error', error.message || 'Connection failed');
        } finally {
            setIsConnecting(false);
        }
    };

    return {
        isConnected,
        isConnecting,
        storageStatus,
        handleConnect,
        checkConnection,
        updateDeviceStatus,
        BASE_URL
    };
}
