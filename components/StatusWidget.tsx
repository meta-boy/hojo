import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { StorageStatus } from '../utils/fileManagerApi';

interface StatusWidgetProps {
    theme: any;
    isConnected: boolean;
    isConnecting: boolean;
    storageStatus: StorageStatus | null;
    onConnect: (silent: boolean) => void;
}

export const StatusWidget: React.FC<StatusWidgetProps> = ({
    theme,
    isConnected,
    isConnecting,
    storageStatus,
    onConnect,
}) => {
    const formatBytes = (bytes: number) => {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    };

    const getFreeSpace = () => {
        if (!storageStatus) return 'Unknown';
        const free = storageStatus.totalBytes - storageStatus.usedBytes;
        return formatBytes(free);
    };

    return (
        <View style={[styles.widget, { borderColor: theme.border, backgroundColor: theme.windowBg }]}>
            <View style={styles.widgetHeader}>
                <Text style={[styles.widgetTitle, { color: theme.text }]}>Device Status</Text>
                <View style={[styles.statusIndicator, { backgroundColor: isConnected ? theme.success : theme.error }]} />
            </View>

            <View style={styles.statusRow}>
                <Text style={[styles.statusLabel, { color: theme.subText }]}>Connection</Text>
                <Text style={[styles.statusValue, { color: isConnected ? theme.success : theme.error }]}>
                    {isConnected ? 'Connected' : 'Disconnected'}
                </Text>
            </View>
            <View style={styles.statusRow}>
                <Text style={[styles.statusLabel, { color: theme.subText }]}>Firmware</Text>
                <Text style={[styles.statusValue, { color: theme.text }]}>
                    {storageStatus?.version || 'Unknown'}
                </Text>
            </View>
            <View style={styles.statusRow}>
                <Text style={[styles.statusLabel, { color: theme.subText }]}>Free Space</Text>
                <Text style={[styles.statusValue, { color: theme.text }]}>
                    {getFreeSpace()}
                </Text>
            </View>

            {!isConnected && (
                <TouchableOpacity
                    style={[styles.connectButton, { backgroundColor: theme.primary }]}
                    onPress={() => onConnect(false)}
                    disabled={isConnecting}
                >
                    <Text style={styles.connectButtonText}>
                        {isConnecting ? 'Connecting...' : 'Connect Now'}
                    </Text>
                </TouchableOpacity>
            )}
        </View>
    );
};

const styles = StyleSheet.create({
    widget: {
        borderRadius: 12,
        padding: 16,
        borderWidth: 1,
        marginBottom: 30,
    },
    widgetHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 16,
    },
    widgetTitle: {
        fontSize: 14,
        fontWeight: '600',
        textTransform: 'uppercase',
        letterSpacing: 0.5,
    },
    statusIndicator: {
        width: 8,
        height: 8,
        borderRadius: 4,
    },
    statusRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        marginBottom: 8,
    },
    statusLabel: {
        fontSize: 14,
    },
    statusValue: {
        fontSize: 14,
        fontWeight: '500',
    },
    connectButton: {
        marginTop: 12,
        paddingVertical: 10,
        borderRadius: 8,
        alignItems: 'center',
    },
    connectButtonText: {
        color: '#FFFFFF',
        fontWeight: '600',
    },
});
