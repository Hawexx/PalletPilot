import { BookCheck } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion';

type RuleExplanationProps = {
  rules: { rule: string; explanation: string }[];
};

export function RuleExplanation({ rules }: RuleExplanationProps) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-start gap-4 space-y-0">
        <div className="bg-primary/10 p-2 rounded-md">
            <BookCheck className="h-6 w-6 text-primary" />
        </div>
        <div className="flex-1">
            <CardTitle>Rule-Based Algorithm Explanation</CardTitle>
        </div>
      </CardHeader>
      <CardContent>
        <Accordion type="single" collapsible className="w-full">
          {rules.map((rule, index) => (
            <AccordionItem value={`item-${index}`} key={index}>
              <AccordionTrigger>{rule.rule}</AccordionTrigger>
              <AccordionContent>
                {rule.explanation}
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      </CardContent>
    </Card>
  );
}
