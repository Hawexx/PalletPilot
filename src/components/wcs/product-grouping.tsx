import { Boxes } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export function ProductGrouping({ explanation }: { explanation: string }) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-start gap-4 space-y-0">
        <div className="bg-primary/10 p-2 rounded-md">
            <Boxes className="h-6 w-6 text-primary" />
        </div>
        <div className="flex-1">
            <CardTitle>Product Grouping Logic</CardTitle>
        </div>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground">{explanation}</p>
      </CardContent>
    </Card>
  );
}
