import { FileItem } from './fileManagerApi';

const cache = new Map<string, FileItem[]>();

export const FileCache = {
    get: (path: string) => cache.get(path),
    set: (path: string, data: FileItem[]) => cache.set(path, data),
    has: (path: string) => cache.has(path),
    invalidate: (path: string) => cache.delete(path),
    clear: () => cache.clear(),
};
