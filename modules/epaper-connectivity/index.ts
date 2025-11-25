import { NativeModules } from 'react-native';

const { EpaperConnectivityModule } = NativeModules;

export interface EpaperNetworkInfo {
    connected: boolean;
    ssid: string;
    targetIp: string;
    targetPort: number;
    hasWifi: boolean;
    hasInternet: boolean;
    interfaceName?: string;
}

export interface EpaperConnectivity {
    /**
     * Phase 2: Connect to the E-Paper hotspot
     * Uses WifiNetworkSpecifier to connect to the local-only Wi-Fi network
     * @returns Promise<boolean> - Resolves true on successful connection
     */
    connectToEpaperHotspot(): Promise<boolean>;

    /**
     * Phase 3: Bind application traffic to the E-Paper network
     * Call this before making API requests to 192.168.3.3
     * @returns Promise<boolean> - Resolves true on successful binding
     */
    bindToEpaperNetwork(): Promise<boolean>;

    /**
     * Phase 3: Unbind from E-Paper network
     * Call this before making API requests to cloud services
     * Allows traffic to route through default network (cellular/internet)
     */
    unbindNetwork(): void;

    /**
     * Phase 4: Test E-Paper device connectivity
     * Performs a socket connection test to verify device is reachable
     * @returns Promise<boolean> - Resolves true if device is accessible
     */
    testEpaperConnection(): Promise<boolean>;

    /**
     * Disconnect from E-Paper hotspot and cleanup
     * Unregisters network callback and clears stored network object
     */
    disconnectEpaperHotspot(): void;

    /**
     * Check if currently connected to E-Paper network
     * @returns Promise<boolean> - Resolves true if connected
     */
    isConnected(): Promise<boolean>;

    /**
     * Get detailed network information
     * @returns Promise<EpaperNetworkInfo | null> - Network details or null if not connected
     */
    getNetworkInfo(): Promise<EpaperNetworkInfo | null>;
}

export default EpaperConnectivityModule as EpaperConnectivity;
