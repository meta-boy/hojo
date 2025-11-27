import { Link } from 'lucide-react-native';
import React from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Modal, Platform, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';

interface QuickLinkModalProps {
    visible: boolean;
    theme: any;
    url: string;
    converting: boolean;
    onClose: () => void;
    onChangeUrl: (text: string) => void;
    onSubmit: () => void;
}

export const QuickLinkModal: React.FC<QuickLinkModalProps> = ({
    visible,
    theme,
    url,
    converting,
    onClose,
    onChangeUrl,
    onSubmit,
}) => {
    return (
        <Modal
            visible={visible}
            transparent={true}
            animationType="fade"
            onRequestClose={onClose}
        >
            <KeyboardAvoidingView
                behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
                style={styles.modalOverlay}
            >
                <View style={[styles.modalContent, { backgroundColor: theme.headerBg, borderColor: theme.border, borderWidth: 1 }]}>
                    <View style={styles.iconCircle}>
                        <Link size={24} color={theme.primary} />
                    </View>
                    <Text style={[styles.modalTitle, { color: theme.text }]}>New Quick Link</Text>
                    <Text style={[styles.modalSubtitle, { color: theme.subText }]}>
                        Paste a URL below to convert and send it to your device instantly.
                    </Text>

                    <TextInput
                        style={[styles.input, { color: theme.text, backgroundColor: theme.windowBg }]}
                        value={url}
                        onChangeText={onChangeUrl}
                        placeholder="https://..."
                        placeholderTextColor={theme.subText}
                        autoCapitalize="none"
                        autoCorrect={false}
                        keyboardType="url"
                    />

                    <View style={styles.modalButtons}>
                        <TouchableOpacity
                            style={[styles.modalButton, styles.cancelButton]}
                            onPress={onClose}
                            disabled={converting}
                        >
                            <Text style={styles.cancelButtonText}>Cancel</Text>
                        </TouchableOpacity>
                        <TouchableOpacity
                            style={[styles.modalButton, { backgroundColor: theme.primary, opacity: converting ? 0.7 : 1 }]}
                            onPress={onSubmit}
                            disabled={converting || !url}
                        >
                            {converting ? (
                                <ActivityIndicator size="small" color="#FFFFFF" />
                            ) : (
                                <Text style={styles.submitButtonText}>Send Link</Text>
                            )}
                        </TouchableOpacity>
                    </View>
                </View>
            </KeyboardAvoidingView>
        </Modal>
    );
};

const styles = StyleSheet.create({
    modalOverlay: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.7)',
        justifyContent: 'center',
        padding: 24,
    },
    modalContent: {
        borderRadius: 24,
        padding: 24,
        maxWidth: 400,
        width: '100%',
        alignSelf: 'center',
        alignItems: 'center',
    },
    iconCircle: {
        width: 56,
        height: 56,
        borderRadius: 28,
        backgroundColor: 'rgba(255,255,255,0.05)',
        alignItems: 'center',
        justifyContent: 'center',
        marginBottom: 16,
    },
    modalTitle: {
        fontSize: 20,
        fontWeight: '700',
        marginBottom: 8,
        textAlign: 'center',
    },
    modalSubtitle: {
        fontSize: 14,
        marginBottom: 24,
        textAlign: 'center',
        lineHeight: 20,
    },
    input: {
        width: '100%',
        borderRadius: 12,
        padding: 16,
        fontSize: 16,
        marginBottom: 24,
    },
    modalButtons: {
        flexDirection: 'row',
        gap: 12,
        width: '100%',
    },
    modalButton: {
        flex: 1,
        padding: 16,
        borderRadius: 12,
        alignItems: 'center',
        justifyContent: 'center',
    },
    cancelButton: {
        backgroundColor: 'transparent',
    },
    cancelButtonText: {
        color: '#9CA3AF',
        fontWeight: '600',
        fontSize: 16,
    },
    submitButtonText: {
        color: '#FFFFFF',
        fontWeight: '600',
        fontSize: 16,
    },
});
