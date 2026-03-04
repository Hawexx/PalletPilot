import { Scale } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export function LoadBalance({ visualization }: { visualization: string }) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">Load Balance</CardTitle>
        <Scale className="h-4 w-4 text-muted-foreground" />
      </CardHeader>
      <CardContent>
        <pre className="mt-2 text-xs font-mono bg-muted p-3 rounded-md overflow-x-auto">
          {visualization}
        </pre>
      </CardContent>
    </Card>
  );
}
