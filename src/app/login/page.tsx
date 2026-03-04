'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { login } from '../actions';
import { Package, Lock, User, ArrowRight } from 'lucide-react';

export default function LoginPage() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const router = useRouter();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setIsLoading(true);

        // tiny delay for visual feedback
        await new Promise(r => setTimeout(r, 600));

        const result = await login(username, password);
        if (result.success) {
            router.push('/');
        } else {
            setError(result.error || 'Login failed');
            setIsLoading(false);
        }
    };

    return (
        <div className="relative flex min-h-screen items-center justify-center bg-black font-sans">
            {/* Grid Pattern Background */}
            <div className="absolute inset-0 bg-[linear-gradient(to_right,#202020_1px,transparent_1px),linear-gradient(to_bottom,#202020_1px,transparent_1px)] bg-[size:4rem_4rem] [mask-image:radial-gradient(ellipse_60%_50%_at_50%_0%,#000_70%,transparent_100%)]" />

            {/* Content Container */}
            <div className="relative z-10 w-full max-w-[400px] animate-in fade-in slide-in-from-bottom-4 duration-700">
                <div className="overflow-hidden border border-white/20 bg-black/40 backdrop-blur-md">
                    <div className="p-8 pt-12">
                        {/* Header */}
                        <div className="mb-10 text-center">
                            <div className="mx-auto mb-6 flex h-14 w-14 items-center justify-center border border-white bg-white">
                                <Package className="h-6 w-6 text-black" />
                            </div>
                            <h1 className="text-3xl font-bold tracking-tight text-white uppercase">PalletPilot</h1>
                            <p className="mt-2 text-xs font-mono uppercase tracking-[0.2em] text-zinc-500">
                                System Access v2.0
                            </p>
                        </div>

                        {error && (
                            <div className="mb-6 border border-white/50 bg-white/5 p-3 text-center text-xs font-mono text-white">
                                [ERROR]: {error}
                            </div>
                        )}

                        <form onSubmit={handleSubmit} className="space-y-6">
                            <div className="space-y-1">
                                <div className="relative group">
                                    <input
                                        type="text"
                                        value={username}
                                        onChange={(e) => setUsername(e.target.value)}
                                        className="peer block w-full border-b border-zinc-700 bg-transparent py-2.5 text-sm text-white placeholder-transparent focus:border-white focus:outline-none transition-colors rounded-none"
                                        placeholder="Username"
                                        id="username"
                                        required
                                    />
                                    <label htmlFor="username" className="absolute left-0 -top-3.5 text-xs text-zinc-500 transition-all peer-placeholder-shown:text-sm peer-placeholder-shown:text-zinc-500 peer-placeholder-shown:top-2.5 peer-focus:-top-3.5 peer-focus:text-xs peer-focus:text-white uppercase tracking-wider">
                                        Username
                                    </label>
                                </div>
                            </div>

                            <div className="space-y-1">
                                <div className="relative group">
                                    <input
                                        type="password"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        className="peer block w-full border-b border-zinc-700 bg-transparent py-2.5 text-sm text-white placeholder-transparent focus:border-white focus:outline-none transition-colors rounded-none"
                                        placeholder="Password"
                                        id="password"
                                        required
                                    />
                                    <label htmlFor="password" className="absolute left-0 -top-3.5 text-xs text-zinc-500 transition-all peer-placeholder-shown:text-sm peer-placeholder-shown:text-zinc-500 peer-placeholder-shown:top-2.5 peer-focus:-top-3.5 peer-focus:text-xs peer-focus:text-white uppercase tracking-wider">
                                        Password
                                    </label>
                                </div>
                            </div>

                            <button
                                type="submit"
                                disabled={isLoading}
                                className="group relative mt-8 flex w-full items-center justify-center bg-white py-3 text-sm font-bold text-black transition-all hover:bg-zinc-200 disabled:opacity-50 disabled:cursor-not-allowed uppercase tracking-widest"
                            >
                                {isLoading ? (
                                    <div className="h-4 w-4 animate-spin rounded-full border-2 border-black/30 border-t-black" />
                                ) : (
                                    <span className="flex items-center gap-2">
                                        Authenticate <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
                                    </span>
                                )}
                            </button>
                        </form>
                    </div>

                    {/* Footer */}
                    <div className="border-t border-white/10 bg-white/5 p-4 text-center">
                        <p className="text-[10px] text-zinc-600 font-mono uppercase">
                            Restricted Area • WCS Operations
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
}
