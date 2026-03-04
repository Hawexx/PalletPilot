'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Sparkles, LoaderCircle } from 'lucide-react';
import type { Dispatch, SetStateAction } from 'react';

import { Button } from '@/components/ui/button';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { getMixPalletAlgorithmPreview } from '@/app/actions';
import type { MixPalletAlgorithmPreviewOutput } from '@/app/actions';

const formSchema = z.object({
  orderRequirements: z.string().min(20, {
    message: 'Order requirements must be at least 20 characters.',
  }),
  palletConstraints: z.string().min(20, {
    message: 'Pallet constraints must be at least 20 characters.',
  }),
  algorithmGoals: z.string().min(20, {
    message: 'Algorithm goals must be at least 20 characters.',
  }),
});

type OrderConfigurationFormProps = {
  setPreview: Dispatch<SetStateAction<MixPalletAlgorithmPreviewOutput | null>>;
  setIsLoading: Dispatch<SetStateAction<boolean>>;
  setError: Dispatch<SetStateAction<string | null>>;
  isGenerating: boolean;
};

export function OrderConfigurationForm({
  setPreview,
  setIsLoading,
  setError,
  isGenerating,
}: OrderConfigurationFormProps) {
  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      orderRequirements: 'Fulfill an order for 12 units of Product A (30x20x15cm, 5kg), 8 units of Product B (40x30x20cm, 8kg), and 20 units of Product C (25x25x25cm, 3kg).',
      palletConstraints: 'Standard pallet (120x100cm base), max height 180cm, max weight 1000kg. Products must not overhang pallet edges.',
      algorithmGoals: 'Prioritize stability by placing heavier items at the bottom. Group identical products into layers. Ensure the final center-of-gravity is as close to the pallet center as possible.',
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    setIsLoading(true);
    setError(null);
    setPreview(null);
    const result = await getMixPalletAlgorithmPreview(values);
    if (result.error) {
      setError(result.error);
    } else {
      setPreview(result.data);
    }
    setIsLoading(false);
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Order Configuration</CardTitle>
        <CardDescription>
          Adjust order parameters to dynamically preview pallet composition.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
            <FormField
              control={form.control}
              name="orderRequirements"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Order Requirements</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="e.g., Product types, quantities, and dimensions"
                      className="resize-y min-h-[100px]"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="palletConstraints"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Pallet Constraints</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="e.g., Max weight, dimensions, stacking rules"
                      className="resize-y min-h-[100px]"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="algorithmGoals"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Algorithm Goals</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="e.g., Optimize for stability, weight distribution, etc."
                      className="resize-y min-h-[100px]"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button type="submit" className="w-full" disabled={isGenerating}>
              {isGenerating ? (
                <LoaderCircle className="animate-spin" />
              ) : (
                <Sparkles />
              )}
              <span className="ml-2">
                {isGenerating ? 'Generating...' : 'Generate Preview'}
              </span>
            </Button>
          </form>
        </Form>
      </CardContent>
    </Card>
  );
}
