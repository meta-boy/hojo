import React from 'react';
import { Dimensions, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

interface AppDockProps {
    theme: any;
    onAction: (action: string) => void;
}

export const AppDock: React.FC<AppDockProps> = ({ theme, onAction }) => {
    return (
        <View>
            <Text style={[styles.sectionTitle, { color: theme.subText }]}>Applications</Text>
            <View style={styles.dockGrid}>
                <TouchableOpacity
                    style={[styles.dockItem, { backgroundColor: theme.windowBg, borderColor: theme.border }]}
                    onPress={() => onAction('File Manager')}
                >
                    <Text style={styles.dockIcon}>📂</Text>
                    <Text style={[styles.dockLabel, { color: theme.text }]}>File Manager</Text>
                </TouchableOpacity>

                <TouchableOpacity
                    style={[styles.dockItem, { backgroundColor: theme.windowBg, borderColor: theme.border }]}
                    onPress={() => onAction('Convert Files')}
                >
                    <Text style={styles.dockIcon}>📄</Text>
                    <Text style={[styles.dockLabel, { color: theme.text }]}>Convert</Text>
                </TouchableOpacity>

                <TouchableOpacity
                    style={[styles.dockItem, { backgroundColor: theme.windowBg, borderColor: theme.border }]}
                    onPress={() => onAction('Quick Link')}
                >
                    <Text style={styles.dockIcon}>🔗</Text>
                    <Text style={[styles.dockLabel, { color: theme.text }]}>Quick Link</Text>
                </TouchableOpacity>
            </View>
        </View>
    );
};

const styles = StyleSheet.create({
    sectionTitle: {
        fontSize: 12,
        fontWeight: '600',
        textTransform: 'uppercase',
        letterSpacing: 1,
        marginBottom: 12,
        marginLeft: 4,
    },
    dockGrid: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: 16,
    },
    dockItem: {
        width: (Dimensions.get('window').width - 40 - 32 - 16) / 3, // Calculate width for 3 columns (approx)
        aspectRatio: 1,
        borderRadius: 16,
        borderWidth: 1,
        alignItems: 'center',
        justifyContent: 'center',
        padding: 8,
    },
    dockIcon: {
        fontSize: 32,
        marginBottom: 8,
    },
    dockLabel: {
        fontSize: 12,
        fontWeight: '500',
        textAlign: 'center',
    },
});
