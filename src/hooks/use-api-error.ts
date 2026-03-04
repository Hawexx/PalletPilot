import { useToast } from './use-toast';
import axios from 'axios';

/**
 * API error response from PalletPilot backend.
 * Matches the ApiErrorResponse DTO structure.
 */
export interface ApiErrorResponse {
    status: number;
    error: string;
    message: string;
    path: string;
    timestamp: string;
    traceId?: string;
}

/**
 * Hook for handling API errors consistently.
 * Shows toast notifications based on HTTP status code.
 *
 * Usage:
 *   const { handleApiError } = useApiError();
 *   try { ... } catch (err) { handleApiError(err); }
 */
export function useApiError() {
    const { toast } = useToast();

    const handleApiError = async (error: unknown) => {
        // Axios errors (primary — this is what our api.ts client throws)
        if (axios.isAxiosError(error)) {
            const status = error.response?.status;

            if (status === 429) {
                toast({
                    title: 'Çok fazla istek',
                    description: 'Lütfen bir dakika bekleyip tekrar deneyin.',
                    variant: 'destructive',
                });
                return;
            }

            if (status === 401 || status === 403) {
                toast({
                    title: 'Yetkilendirme hatası',
                    description: 'Oturum süreniz dolmuş olabilir.',
                    variant: 'destructive',
                });
                return;
            }

            // Timeout
            if (error.code === 'ECONNABORTED') {
                toast({
                    title: 'Zaman aşımı',
                    description: 'Sunucu yanıt vermedi. Lütfen tekrar deneyin.',
                    variant: 'destructive',
                });
                return;
            }

            // Network error (no response received)
            if (!error.response) {
                toast({
                    title: 'Bağlantı hatası',
                    description: 'Sunucuya bağlanılamıyor. İnternet bağlantınızı kontrol edin.',
                    variant: 'destructive',
                });
                return;
            }

            // Server responded with an error
            const body = error.response.data as Partial<ApiErrorResponse> | undefined;
            toast({
                title: `Hata (${status})`,
                description: body?.message || error.message || 'Beklenmeyen bir hata oluştu.',
                variant: 'destructive',
            });
            return;
        }

        // Fetch/Response errors (fallback for non-axios calls)
        if (error instanceof Response) {
            const status = error.status;
            try {
                const body: ApiErrorResponse = await error.json();
                toast({
                    title: `Hata (${status})`,
                    description: body.message || 'Beklenmeyen bir hata oluştu.',
                    variant: 'destructive',
                });
            } catch {
                toast({
                    title: `Hata (${status})`,
                    description: 'Sunucu hatası. Lütfen tekrar deneyin.',
                    variant: 'destructive',
                });
            }
            return;
        }

        // Generic errors
        const message = error instanceof Error ? error.message : 'Bilinmeyen bir hata oluştu.';
        toast({
            title: 'Hata',
            description: message,
            variant: 'destructive',
        });
    };

    return { handleApiError };
}
