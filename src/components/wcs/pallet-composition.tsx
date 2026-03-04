import { Layers } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export function PalletComposition({ composition }: { composition: string }) {
  const layers = composition.split(/(?=Layer \d+:)/g).map(s => s.trim());
  return (
    <Card>
      <CardHeader className="flex flex-row items-start gap-4 space-y-0">
         <div className="bg-primary/10 p-2 rounded-md">
            <Layers className="h-6 w-6 text-primary" />
        </div>
        <div className="flex-1">
            <CardTitle>Pallet Composition</CardTitle>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-4 text-sm text-muted-foreground">
          {layers.map((layer, index) => {
            if (!layer) return null;
            const parts = layer.split(/ - |:/);
            const title = parts.shift() + (parts.length > 1 ? ':' : '');
            const content = parts.join(' - ');
            
            return (
              <div key={index}>
                <p className="font-semibold text-foreground">{title}</p>
                <p>{content}</p>
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}
