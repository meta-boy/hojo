import React from 'react';
import { Modal, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';

interface InputModalProps {
    visible: boolean;
    theme: any;
    title: string;
    value: string;
    placeholder: string;
    submitLabel: string;
    onClose: () => void;
    onChangeText: (text: string) => void;
    onSubmit: () => void;
}

export const InputModal: React.FC<InputModalProps> = ({
    visible,
    theme,
    title,
    value,
    placeholder,
    submitLabel,
    onClose,
    onChangeText,
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
                    <Text style={[styles.modalTitle, { color: theme.text }]}>
                        {title}
                    </Text>
                    <TextInput
                        style={[styles.input, { color: theme.text, borderColor: theme.border, backgroundColor: theme.windowBg }]}
                        value={value}
                        onChangeText={onChangeText}
                        placeholder={placeholder}
                        placeholderTextColor={theme.subText}
                        autoFocus
                    />
                    <View style={styles.modalButtons}>
                        <TouchableOpacity
                            style={[styles.modalButton, styles.cancelButton]}
                            onPress={onClose}
                        >
                            <Text style={styles.cancelButtonText}>Cancel</Text>
                        </TouchableOpacity>
                        <TouchableOpacity
                            style={[styles.modalButton, { backgroundColor: theme.primary }]}
                            onPress={onSubmit}
                        >
                            <Text style={styles.submitButtonText}>
                                {submitLabel}
                            </Text>
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
        marginBottom: 16,
        textAlign: 'center',
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
