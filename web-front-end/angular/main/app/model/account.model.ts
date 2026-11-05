export interface Account {
    id: number;
    displayName: string;
}

export function maskAccountNumber(accountId?: number | string | null): string {
    if (accountId === undefined || accountId === null) {
        return '';
    }
    return '****' + String(accountId).slice(-4);
}
