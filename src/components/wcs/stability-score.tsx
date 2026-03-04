import { CheckCircle2 } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';

export function StabilityScore({ score }: { score: number }) {
  const value = Math.min(Math.max(score, 0), 100);

  let progressColor;
  if (value < 50) {
    progressColor = 'bg-destructive';
  } else if (value < 80) {
    progressColor = 'bg-chart-4';
  } else {
    progressColor = 'bg-chart-2';
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">Stability Score</CardTitle>
        <CheckCircle2 className="h-4 w-4 text-muted-foreground" />
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}/100</div>
        <Progress value={value} className="mt-2 h-2" indicatorClassName={progressColor} />
      </CardContent>
    </Card>
  );
}
