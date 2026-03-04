'use client';

import { FileCog, AlertTriangle } from 'lucide-react';
import type { MixPalletAlgorithmPreviewOutput } from '@/app/actions';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { StabilityScore } from './stability-score';
import { LoadBalance } from './load-balance';
import { ProductGrouping } from './product-grouping';
import { PalletComposition } from './pallet-composition';
import { RuleExplanation } from './rule-explanation';

type AlgorithmPreviewProps = {
  preview: MixPalletAlgorithmPreviewOutput | null;
  isLoading: boolean;
  error: string | null;
};

export function AlgorithmPreview({ preview, isLoading, error }: AlgorithmPreviewProps) {
  const renderContent = () => {
    if (isLoading) {
      return <LoadingSkeleton />;
    }

    if (error) {
      return (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>Error Generating Preview</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      );
    }

    if (!preview) {
      return <InitialState />;
    }

    return (
      <div className="space-y-6">
        <div className="grid gap-6 md:grid-cols-2">
          <StabilityScore score={preview.stabilityScore} />
          <LoadBalance visualization={preview.weightBalanceVisualization} />
        </div>
        <ProductGrouping explanation={preview.productGroupingExplanation} />
        <PalletComposition composition={preview.palletComposition} />
        <RuleExplanation rules={preview.ruleExplanation} />
      </div>
    );
  };

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>Algorithm Decision Preview</CardTitle>
        <CardDescription>
          A visual representation of the pallet building logic and outcome.
        </CardDescription>
      </CardHeader>
      <CardContent>{renderContent()}</CardContent>
    </Card>
  );
}

const InitialState = () => (
  <div className="flex flex-col items-center justify-center text-center text-muted-foreground p-8 rounded-lg border-2 border-dashed h-full min-h-[300px]">
    <FileCog className="h-12 w-12 mb-4" />
    <h3 className="text-lg font-semibold mb-1">Awaiting Configuration</h3>
    <p className="max-w-xs">
      Fill out the order configuration and click 'Generate Preview' to see the
      algorithm's proposed pallet solution.
    </p>
  </div>
);

const LoadingSkeleton = () => (
  <div className="space-y-6">
    <div className="grid gap-6 md:grid-cols-2">
      <div className="space-y-2">
        <Skeleton className="h-8 w-1/3" />
        <Skeleton className="h-6 w-full" />
        <Skeleton className="h-4 w-1/4" />
      </div>
       <div className="space-y-2">
        <Skeleton className="h-8 w-1/3" />
        <Skeleton className="h-20 w-full" />
      </div>
    </div>
    <div className="space-y-2">
      <Skeleton className="h-8 w-1/4" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-4/5" />
    </div>
     <div className="space-y-2">
      <Skeleton className="h-8 w-1/3" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-3/4" />
    </div>
  </div>
);
