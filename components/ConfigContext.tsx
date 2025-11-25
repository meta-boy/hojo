import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useContext, useEffect, useState } from 'react';
import { fetchStatus } from '../utils/fileManagerApi';

interface ConfigContextType {
    ipAddress: string;
    setIpAddress: (ip: string) => void;
    baseUrl: string;
}

const ConfigContext = createContext<ConfigContextType>({
    ipAddress: 'e-paper.local',
    setIpAddress: () => { },
    baseUrl: 'http://e-paper.local',
});

export const useConfig = () => useContext(ConfigContext);

export const ConfigProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [ipAddress, setIpAddress] = useState('e-paper.local');

    useEffect(() => {
        AsyncStorage.getItem('ipAddress').then(ip => {
            if (ip) setIpAddress(ip);
        });
    }, []);

    const updateIpAddress = (ip: string) => {
        setIpAddress(ip);
        AsyncStorage.setItem('ipAddress', ip).catch(console.error);
    };

    const baseUrl = `http://${ipAddress}`;

    // Keep-alive ping
    useEffect(() => {
        const interval = setInterval(() => {
            // console.log('Pinging status...');
            fetchStatus(baseUrl).catch(() => {
                // Ignore errors, just trying to keep connection alive
            });
        }, 5000);

        return () => clearInterval(interval);
    }, [baseUrl]);

    return (
        <ConfigContext.Provider value={{ ipAddress, setIpAddress: updateIpAddress, baseUrl }}>
            {children}
        </ConfigContext.Provider>
    );
};
