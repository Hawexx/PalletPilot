'use client';
import { usePathname } from "next/navigation";
import {
    LayoutDashboard,
    Package,
    Boxes,
    Server,
    Network,
    AlertTriangle,
    Settings,
    Cuboid,
    BarChart3,
    Database,
    Radio,
} from "lucide-react";
import { SidebarProvider, Sidebar, SidebarHeader, SidebarContent, SidebarMenu, SidebarMenuItem, SidebarMenuButton, SidebarInset, SidebarTrigger } from "@/components/ui/sidebar";
import { Package2 } from "lucide-react";
import Link from "next/link";

const menuItems = [
    { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
    { href: "/orders", label: "Order Management", icon: Package },
    { href: "/pallet-planning", label: "Pallet Planning", icon: Cuboid },
    { href: "/slotting", label: "Slotting Optimization", icon: BarChart3 },
    { href: "/erp-integration", label: "ERP Integration", icon: Database },
    { href: "/movu-operations", label: "Movu Operations", icon: Radio },
    { href: "/equipment", label: "Equipment Control", icon: Server },
    { href: "/warehouse-flow", label: "Warehouse Flow", icon: Network },
    { href: "/alerts", label: "Alerts", icon: AlertTriangle },
    { href: "/settings", label: "Settings", icon: Settings },
];

export default function WcsLayout({ children }: { children: React.ReactNode }) {
    const pathname = usePathname();
    return (
        <SidebarProvider>
            <Sidebar>
                <SidebarHeader>
                    <div className="flex items-center gap-2 p-2">
                        <Package2 className="h-6 w-6 text-primary" />
                        <span className="font-headline text-xl group-data-[collapsible=icon]:hidden">PalletPilot</span>
                    </div>
                </SidebarHeader>
                <SidebarContent>
                    <SidebarMenu>
                        {menuItems.map((item) => (
                            <SidebarMenuItem key={item.label}>
                                <SidebarMenuButton tooltip={item.label} isActive={pathname === item.href} asChild>
                                    <Link href={item.href}>
                                        <item.icon />
                                        <span>{item.label}</span>
                                    </Link>
                                </SidebarMenuButton>
                            </SidebarMenuItem>
                        ))}
                    </SidebarMenu>
                </SidebarContent>
            </Sidebar>
            <SidebarInset>
                <header className="sticky top-0 z-10 flex h-16 items-center justify-between gap-4 border-b bg-background px-4 md:px-6">
                    <SidebarTrigger className="md:hidden" />
                    <div className="flex w-full items-center justify-end gap-4">
                        <Link href="/login" className="flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium hover:bg-accent hover:text-accent-foreground text-red-500">
                            <span>Log out</span>
                        </Link>
                    </div>
                </header>
                <main className="flex-1 p-4 sm:p-6 md:p-8">
                    {children}
                </main>
            </SidebarInset>
        </SidebarProvider>
    );
}
