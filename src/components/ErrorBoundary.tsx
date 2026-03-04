'use client';

import React, { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
    children: ReactNode;
    fallback?: ReactNode;
}

interface State {
    hasError: boolean;
    error: Error | null;
}

/**
 * Global Error Boundary — catches unhandled React rendering errors.
 * Shows a graceful fallback UI with retry capability.
 */
export class ErrorBoundary extends Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { hasError: false, error: null };
    }

    static getDerivedStateFromError(error: Error): State {
        return { hasError: true, error };
    }

    componentDidCatch(error: Error, errorInfo: ErrorInfo) {
        console.error('[ErrorBoundary] Caught error:', error, errorInfo);
    }

    handleRetry = () => {
        this.setState({ hasError: false, error: null });
    };

    render() {
        if (this.state.hasError) {
            if (this.props.fallback) {
                return this.props.fallback;
            }

            return (
                <div style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    minHeight: '400px',
                    padding: '2rem',
                    textAlign: 'center',
                    fontFamily: 'Inter, system-ui, sans-serif',
                }}>
                    <div style={{
                        fontSize: '3rem',
                        marginBottom: '1rem',
                    }}>⚠️</div>
                    <h2 style={{
                        fontSize: '1.5rem',
                        fontWeight: 600,
                        color: '#1a1a2e',
                        marginBottom: '0.5rem',
                    }}>Bir Hata Oluştu</h2>
                    <p style={{
                        color: '#666',
                        marginBottom: '1.5rem',
                        maxWidth: '400px',
                    }}>
                        Beklenmeyen bir hata ile karşılaşıldı. Lütfen tekrar deneyin.
                    </p>
                    {this.state.error && (
                        <pre style={{
                            background: '#f5f5f5',
                            padding: '0.75rem 1rem',
                            borderRadius: '8px',
                            fontSize: '0.75rem',
                            color: '#cc0000',
                            maxWidth: '500px',
                            overflow: 'auto',
                            marginBottom: '1.5rem',
                        }}>
                            {this.state.error.message}
                        </pre>
                    )}
                    <button
                        onClick={this.handleRetry}
                        style={{
                            padding: '0.75rem 2rem',
                            background: '#3b82f6',
                            color: 'white',
                            border: 'none',
                            borderRadius: '8px',
                            fontSize: '1rem',
                            cursor: 'pointer',
                            fontWeight: 500,
                            transition: 'background 0.2s',
                        }}
                        onMouseOver={(e) => (e.currentTarget.style.background = '#2563eb')}
                        onMouseOut={(e) => (e.currentTarget.style.background = '#3b82f6')}
                    >
                        Tekrar Dene
                    </button>
                </div>
            );
        }

        return this.props.children;
    }
}

export default ErrorBoundary;
