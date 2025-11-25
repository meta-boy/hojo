import React from 'react';
import { ActivityIndicator, Modal, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';

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
            <View style={styles.modalOverlay}>
                <View style={[styles.modalContent, { backgroundColor: theme.contentBg }]}>
                    <Text style={[styles.modalTitle, { color: theme.text }]}>Quick Link to EPUB</Text>
                    <Text style={[styles.modalSubtitle, { color: theme.subText }]}>
                        Enter a URL to convert it to an EPUB and upload it to your device.
                    </Text>

                    <TextInput
                        style={[styles.input, { color: theme.text, borderColor: theme.border, backgroundColor: theme.windowBg }]}
                        value={url}
                        onChangeText={onChangeUrl}
                        placeholder="https://example.com/article"
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
                                <Text style={styles.submitButtonText}>Send to Device</Text>
                            )}
                        </TouchableOpacity>
                    </View>
                </View>
            </View>
        </Modal>
    );
};

const styles = StyleSheet.create({
    modalOverlay: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.5)',
        justifyContent: 'center',
        padding: 20,
    },
    modalContent: {
        borderRadius: 12,
        padding: 24,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 10 },
        shadowOpacity: 0.25,
        shadowRadius: 10,
        elevation: 10,
        maxWidth: 400,
        width: '100%',
        alignSelf: 'center',
    },
    modalTitle: {
        fontSize: 18,
        fontWeight: 'bold',
        marginBottom: 8,
        textAlign: 'center',
    },
    modalSubtitle: {
        fontSize: 14,
        marginBottom: 20,
        textAlign: 'center',
        lineHeight: 20,
    },
    input: {
        borderWidth: 1,
        borderRadius: 6,
        padding: 10,
        fontSize: 14,
        marginBottom: 20,
    },
    modalButtons: {
        flexDirection: 'row',
        gap: 12,
    },
    modalButton: {
        flex: 1,
        padding: 10,
        borderRadius: 6,
        alignItems: 'center',
        justifyContent: 'center',
        height: 44,
    },
    cancelButton: {
        backgroundColor: '#9CA3AF',
    },
    cancelButtonText: {
        color: '#FFFFFF',
        fontWeight: '600',
        fontSize: 14,
    },
    submitButtonText: {
        color: '#FFFFFF',
        fontWeight: '600',
        fontSize: 14,
    },
});
