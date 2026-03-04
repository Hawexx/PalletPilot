import { Bot, Server, ArrowRightLeft } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

type Equipment = {
  name: string;
  icon: React.ReactNode;
  status: 'Online' | 'Offline' | 'In Use';
};

const equipment: Equipment[] = [
  {
    name: 'AS/RS System',
    icon: <Server className="h-5 w-5 text-muted-foreground" />,
    status: 'Online',
  },
  {
    name: 'Robot Arms',
    icon: <Bot className="h-5 w-5 text-muted-foreground" />,
    status: 'In Use',
  },
  {
    name: 'Conveyor Belts',
    icon: <ArrowRightLeft className="h-5 w-5 text-muted-foreground" />,
    status: 'Online',
  },
];

const StatusIndicator = ({ status }: { status: Equipment['status'] }) => {
  const colorClass =
    status === 'Online'
      ? 'bg-chart-2'
      : status === 'In Use'
      ? 'bg-chart-4'
      : 'bg-destructive';
  return <div className={`h-2.5 w-2.5 rounded-full ${colorClass}`} />;
};

export function EquipmentStatus() {
  return (
        <ul className="space-y-4">
          {equipment.map((item) => (
            <li key={item.name} className="flex items-center gap-4">
              {item.icon}
              <span className="font-medium">{item.name}</span>
              <div className="ml-auto flex items-center gap-2">
                <StatusIndicator status={item.status} />
                <span>{item.status}</span>
              </div>
            </li>
          ))}
        </ul>
  );
}
