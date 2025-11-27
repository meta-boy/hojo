import React from 'react';
import { KeyboardAvoidingView, Modal, Platform, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';

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
            <KeyboardAvoidingView
                behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
                style={styles.modalOverlay}
            >
                <View style={[styles.modalContent, { backgroundColor: theme.headerBg, borderColor: theme.border, borderWidth: 1 }]}>
                    <Text style={[styles.modalTitle, { color: theme.text }]}>
                        {title}
                    </Text>
                    <TextInput
                        style={[styles.input, { color: theme.text, backgroundColor: theme.windowBg }]}
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
    },
    modalTitle: {
        fontSize: 18,
        fontWeight: '700',
        marginBottom: 24,
        textAlign: 'center',
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
