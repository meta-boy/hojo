import * as FileSystem from 'expo-file-system';


export interface FileItem {
    name: string;
    type: 'dir' | 'file';
    size?: number;
}

export interface StorageStatus {
    totalBytes: number;
    usedBytes: number;
}

export const fetchList = async (baseUrl: string, path: string): Promise<FileItem[]> => {
    try {
        const res = await fetch(`${baseUrl}/list?dir=${encodeURIComponent(path)}`);
        if (!res.ok) throw new Error('List failed');
        const data: FileItem[] = await res.json();
        return data.sort((a, b) => {
            if (a.type === b.type) return a.name.localeCompare(b.name);
            return a.type === 'dir' ? -1 : 1;
        });
    } catch (e) {
        console.error(e);
        return [];
    }
};

export const fetchStatus = async (baseUrl: string): Promise<StorageStatus> => {
    try {
        const res = await fetch(`${baseUrl}/status`);
        if (!res.ok) throw new Error('Status failed');
        return await res.json();
    } catch (e) {
        console.error(e);
        return { totalBytes: 0, usedBytes: 0 };
    }
};

export const createFolder = async (baseUrl: string, path: string): Promise<void> => {
    const fd = new FormData();
    fd.append("path", path);
    const res = await fetch(`${baseUrl}/edit`, { method: 'PUT', body: fd });
    if (!res.ok) throw new Error('Create failed');
};

export const deleteItem = async (baseUrl: string, path: string): Promise<void> => {
    const fd = new FormData();
    fd.append("path", path);
    const res = await fetch(`${baseUrl}/edit`, { method: 'DELETE', body: fd });
    if (!res.ok) throw new Error('Delete failed');
};

export const renameItem = async (baseUrl: string, from: string, to: string): Promise<void> => {
    const fd = new FormData();
    fd.append("path", to);
    fd.append("src", from);
    const res = await fetch(`${baseUrl}/edit`, { method: 'PUT', body: fd });
    if (!res.ok) throw new Error('Rename failed');
};


const getMimeType = (filePath: string): string => {
    const ext = filePath.split('.').pop()?.toLowerCase();
    const mimeTypes: Record<string, string> = {
        'jpg': 'image/jpeg',
        'jpeg': 'image/jpeg',
        'png': 'image/png',
        'gif': 'image/gif',
        'bmp': 'image/bmp',
        'webp': 'image/webp',
        'svg': 'image/svg+xml',
        'ico': 'image/x-icon',
        'tiff': 'image/tiff',
        'tif': 'image/tiff',
        'mp4': 'video/mp4',
        'mp3': 'audio/mpeg',
        'pdf': 'application/pdf',
        'json': 'application/json',
        'txt': 'text/plain',
        'html': 'text/html',
        'css': 'text/css',
        'js': 'application/javascript',
        'epub': 'application/epub+zip',
    };
    return mimeTypes[ext || ''] || 'application/octet-stream';
};

export const uploadFile = async (
    baseUrl: string,
    fileUri: string,
    fileName: string,
    targetPath: string
): Promise<void> => {
    const mimeType = getMimeType(fileName);

    // Use the new File API to read the file as bytes directly
    const file = new FileSystem.File(fileUri);
    const bytes = await file.bytes();

    // Build multipart body manually
    const boundary = `----WebKitFormBoundary${Date.now()}`;
    
    const header = 
        `--${boundary}\r\n` +
        `Content-Disposition: form-data; name="data"; filename="${targetPath}"\r\n` +
        `Content-Type: ${mimeType}\r\n\r\n`;
    
    const footer = `\r\n--${boundary}--\r\n`;

    // Combine header + file bytes + footer
    const headerBytes = new TextEncoder().encode(header);
    const footerBytes = new TextEncoder().encode(footer);
    
    const body = new Uint8Array(headerBytes.length + bytes.length + footerBytes.length);
    body.set(headerBytes, 0);
    body.set(bytes, headerBytes.length);
    body.set(footerBytes, headerBytes.length + bytes.length);

    const res = await fetch(`${baseUrl}/edit`, {
        method: 'POST',
        headers: {
            'Content-Type': `multipart/form-data; boundary=${boundary}`,
        },
        body: body,
    });

    if (!res.ok) throw new Error('Upload failed');
};