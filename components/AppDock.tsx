import { ChevronRight, Folder, Image, Link } from 'lucide-react-native';
import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

interface AppDockProps {
    theme: any;
    onAction: (action: string) => void;
}

export const AppDock: React.FC<AppDockProps> = ({ theme, onAction }) => {
    const actions = [
        {
            id: 'File Manager',
            label: 'File Manager',
            subLabel: 'Browse and manage files',
            icon: Folder,
        },
        {
            id: 'Quick Link',
            label: 'Quick Link',
            subLabel: 'Push URL to device',
            icon: Link,
        },
        {
            id: 'Wallpaper Editor',
            label: 'Wallpaper',
            subLabel: 'Customize display',
            icon: Image,
        },
    ];

    return (
        <View style={styles.container}>
            <Text style={[styles.sectionTitle, { color: theme.subText }]}>Quick Actions</Text>
            <View style={styles.listContainer}>
                {actions.map((action) => (
                    <TouchableOpacity
                        key={action.id}
                        style={[styles.actionCard, { backgroundColor: theme.headerBg, borderColor: theme.border }]}
                        onPress={() => onAction(action.id)}
                        activeOpacity={0.7}
                    >
                        <View style={[styles.iconBox, { backgroundColor: 'rgba(255,255,255,0.05)' }]}>
                            <action.icon size={24} color={theme.primary} />
                        </View>
                        <View style={styles.textContainer}>
                            <Text style={[styles.actionLabel, { color: theme.text }]}>{action.label}</Text>
                            <Text style={[styles.actionSubLabel, { color: theme.subText }]}>{action.subLabel}</Text>
                        </View>
                        <ChevronRight size={20} color={theme.subText} />
                    </TouchableOpacity>
                ))}
            </View>
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        marginTop: 10,
    },
    sectionTitle: {
        fontSize: 13,
        fontWeight: '700',
        textTransform: 'uppercase',
        letterSpacing: 1.2,
        marginBottom: 16,
        marginLeft: 4,
    },
    listContainer: {
        gap: 12,
    },
    actionCard: {
        flexDirection: 'row',
        alignItems: 'center',
        padding: 16,
        borderRadius: 20,
        borderWidth: 1,
    },
    iconBox: {
        width: 48,
        height: 48,
        borderRadius: 14,
        alignItems: 'center',
        justifyContent: 'center',
        marginRight: 16,
    },
    textContainer: {
        flex: 1,
    },
    actionLabel: {
        fontSize: 16,
        fontWeight: '600',
        marginBottom: 2,
    },
    actionSubLabel: {
        fontSize: 13,
    },
});
