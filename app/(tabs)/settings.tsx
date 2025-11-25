import { useConfig } from '@/components/ConfigContext';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Save } from 'lucide-react-native';
import React, { useEffect, useState } from 'react';
import { Alert, ScrollView, StyleSheet, TextInput, TouchableOpacity } from 'react-native';

export default function SettingsScreen() {
    const { ipAddress, setIpAddress } = useConfig();
    const [localIp, setLocalIp] = useState(ipAddress);

    useEffect(() => {
        setLocalIp(ipAddress);
    }, [ipAddress]);

    const handleSave = () => {
        setIpAddress(localIp);
        Alert.alert('Success', 'IP Address updated successfully');
    };

    return (
        <ThemedView style={styles.container}>
            <ScrollView contentContainerStyle={styles.scrollContent}>
                <ThemedText type="title" style={styles.title}>Settings</ThemedText>

                <ThemedView style={styles.section}>
                    <ThemedText type="subtitle" style={styles.sectionTitle}>Connection</ThemedText>
                    <ThemedText style={styles.label}>Device IP Address / Hostname</ThemedText>
                    <TextInput
                        style={styles.input}
                        value={localIp}
                        onChangeText={setLocalIp}
                        placeholder="e.g., 192.168.1.100 or e-paper.local"
                        placeholderTextColor="#888"
                        autoCapitalize="none"
                        autoCorrect={false}
                    />
                    <ThemedText style={styles.hint}>
                        Default is "e-paper.local". If that doesn't work, enter the device's IP address found in your router settings.
                    </ThemedText>
                </ThemedView>

                <TouchableOpacity style={styles.button} onPress={handleSave}>
                    <Save size={20} color="white" style={styles.buttonIcon} />
                    <ThemedText style={styles.buttonText}>Save Configuration</ThemedText>
                </TouchableOpacity>
            </ScrollView>
        </ThemedView>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    scrollContent: {
        padding: 20,
        paddingTop: 60,
    },
    title: {
        marginBottom: 30,
        fontSize: 34,
    },
    section: {
        marginBottom: 30,
        backgroundColor: 'rgba(150, 150, 150, 0.1)',
        padding: 20,
        borderRadius: 16,
    },
    sectionTitle: {
        marginBottom: 15,
    },
    label: {
        marginBottom: 8,
        fontSize: 16,
        fontWeight: '600',
    },
    input: {
        backgroundColor: '#fff',
        padding: 15,
        borderRadius: 10,
        fontSize: 16,
        borderWidth: 1,
        borderColor: '#ddd',
        marginBottom: 10,
        color: '#000',
    },
    hint: {
        fontSize: 12,
        color: '#888',
        lineHeight: 18,
    },
    button: {
        backgroundColor: '#007AFF',
        padding: 18,
        borderRadius: 12,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        shadowColor: '#007AFF',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3,
        shadowRadius: 8,
        elevation: 5,
    },
    buttonIcon: {
        marginRight: 8,
    },
    buttonText: {
        color: 'white',
        fontSize: 18,
        fontWeight: 'bold',
    },
});
