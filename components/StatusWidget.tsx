import { Cpu, HardDrive, Wifi, WifiOff } from 'lucide-react-native';
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

    const getStoragePercentage = () => {
        if (!storageStatus || storageStatus.totalBytes === 0) return 0;
        return (storageStatus.usedBytes / storageStatus.totalBytes) * 100;
    };

    const usedPercentage = getStoragePercentage();

    return (
        <View style={[styles.widget, { backgroundColor: theme.headerBg, borderColor: theme.border }]}>
            {/* Header */}
            <View style={styles.header}>
                <View style={styles.headerLeft}>
                    <Cpu size={20} color={theme.primary} />
                    <Text style={[styles.title, { color: theme.text }]}>Device Status</Text>
                </View>
                <View style={[styles.badge, { backgroundColor: isConnected ? 'rgba(34, 197, 94, 0.1)' : 'rgba(239, 68, 68, 0.1)' }]}>
                    <Text style={[styles.badgeText, { color: isConnected ? theme.success : theme.error }]}>
                        {isConnected ? 'Online' : 'Offline'}
                    </Text>
                </View>
            </View>

            {/* Connection Status */}
            <View style={styles.row}>
                <View style={styles.iconContainer}>
                    {isConnected ? <Wifi size={18} color={theme.subText} /> : <WifiOff size={18} color={theme.subText} />}
                </View>
                <View style={styles.infoContainer}>
                    <Text style={[styles.label, { color: theme.subText }]}>Connection</Text>
                    <Text style={[styles.value, { color: theme.text }]}>
                        {isConnected ? 'Connected via WiFi' : 'Not Connected'}
                    </Text>
                </View>
                {!isConnected && (
                     <TouchableOpacity
                        style={[styles.smallButton, { backgroundColor: theme.primary }]}
                        onPress={() => onConnect(false)}
                        disabled={isConnecting}
                    >
                        <Text style={styles.smallButtonText}>
                            {isConnecting ? '...' : 'Connect'}
                        </Text>
                    </TouchableOpacity>
                )}
            </View>

            {/* Storage Status */}
            <View style={[styles.row, { marginTop: 16 }]}>
                <View style={styles.iconContainer}>
                    <HardDrive size={18} color={theme.subText} />
                </View>
                <View style={styles.infoContainer}>
                    <View style={styles.storageHeader}>
                        <Text style={[styles.label, { color: theme.subText }]}>Storage</Text>
                        <Text style={[styles.value, { color: theme.text, fontSize: 12 }]}>
                            {storageStatus ? `${formatBytes(storageStatus.usedBytes)} / ${formatBytes(storageStatus.totalBytes)}` : 'Unknown'}
                        </Text>
                    </View>

                    {/* Progress Bar */}
                    <View style={[styles.progressBarBg, { backgroundColor: theme.border }]}>
                        <View
                            style={[
                                styles.progressBarFill,
                                {
                                    backgroundColor: theme.primary,
                                    width: `${usedPercentage}%`
                                }
                            ]}
                        />
                    </View>
                </View>
            </View>

             {/* Footer Info */}
             {storageStatus?.version && (
                <View style={[styles.footer, { borderTopColor: theme.border }]}>
                    <Text style={[styles.footerText, { color: theme.subText }]}>Firmware: {storageStatus.version}</Text>
                </View>
             )}
        </View>
    );
};

const styles = StyleSheet.create({
    widget: {
        borderRadius: 24,
        padding: 20,
        borderWidth: 1,
        marginBottom: 24,
    },
    header: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 24,
    },
    headerLeft: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 10,
    },
    title: {
        fontSize: 16,
        fontWeight: '700',
    },
    badge: {
        paddingHorizontal: 10,
        paddingVertical: 4,
        borderRadius: 12,
    },
    badgeText: {
        fontSize: 12,
        fontWeight: '600',
    },
    row: {
        flexDirection: 'row',
        alignItems: 'flex-start', // Changed to flex-start for multi-line support
    },
    iconContainer: {
        width: 32,
        height: 32,
        alignItems: 'center',
        justifyContent: 'center',
        marginRight: 12,
    },
    infoContainer: {
        flex: 1,
        justifyContent: 'center',
    },
    label: {
        fontSize: 12,
        marginBottom: 2,
    },
    value: {
        fontSize: 14,
        fontWeight: '500',
    },
    storageHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        marginBottom: 8,
    },
    progressBarBg: {
        height: 6,
        borderRadius: 3,
        width: '100%',
        overflow: 'hidden',
    },
    progressBarFill: {
        height: '100%',
        borderRadius: 3,
    },
    smallButton: {
        paddingHorizontal: 12,
        paddingVertical: 6,
        borderRadius: 8,
        marginLeft: 8,
    },
    smallButtonText: {
        color: '#FFF',
        fontSize: 12,
        fontWeight: '600',
    },
    footer: {
        marginTop: 16,
        paddingTop: 12,
        borderTopWidth: 1,
        alignItems: 'flex-end',
    },
    footerText: {
        fontSize: 11,
    }
});
