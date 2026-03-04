import React from 'react';

export default function Sidebar() {
  const onDragStart = (event: React.DragEvent, nodeType: string, label: string) => {
    if (event.dataTransfer) {
      event.dataTransfer.setData('application/reactflow', nodeType);
      event.dataTransfer.setData('application/reactflow-label', label);
      event.dataTransfer.effectAllowed = 'move';
    }
  };

  return (
    <aside className="w-64 bg-gray-100 dark:bg-gray-900 border-r border-gray-200 dark:border-gray-800 p-4 flex flex-col gap-4 h-full">
      <div className="mb-4">
        <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">Equipment</h2>
        <p className="text-sm text-gray-500 dark:text-gray-400">Drag items to the canvas</p>
      </div>

      <div
        className="bg-white dark:bg-gray-800 p-3 rounded-md shadow-sm border border-gray-200 dark:border-gray-700 cursor-grab hover:border-blue-500 transition-colors"
        onDragStart={(event) => onDragStart(event, 'equipment', 'Conveyor')}
        draggable
      >
        <div className="flex items-center gap-2">
           <div className="w-3 h-3 rounded-full bg-blue-500" />
           <span className="font-medium">Conveyor</span>
        </div>
        <div className="text-xs text-gray-500 mt-1">Standard belt conveyor</div>
      </div>

      <div
        className="bg-white dark:bg-gray-800 p-3 rounded-md shadow-sm border border-gray-200 dark:border-gray-700 cursor-grab hover:border-green-500 transition-colors"
        onDragStart={(event) => onDragStart(event, 'equipment', 'Robot')}
        draggable
      >
         <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-green-500" />
            <span className="font-medium">Robot</span>
         </div>
         <div className="text-xs text-gray-500 mt-1">Movu or Kuka Robot</div>
      </div>

      <div
        className="bg-white dark:bg-gray-800 p-3 rounded-md shadow-sm border border-gray-200 dark:border-gray-700 cursor-grab hover:border-purple-500 transition-colors"
        onDragStart={(event) => onDragStart(event, 'equipment', 'Wrapper')}
        draggable
      >
        <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-purple-500" />
            <span className="font-medium">Wrapper</span>
        </div>
        <div className="text-xs text-gray-500 mt-1">Pallet Wrapper</div>
      </div>

      <div
        className="bg-white dark:bg-gray-800 p-3 rounded-md shadow-sm border border-gray-200 dark:border-gray-700 cursor-grab hover:border-orange-500 transition-colors"
        onDragStart={(event) => onDragStart(event, 'equipment', 'Palletizer')}
        draggable
      >
         <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-orange-500" />
            <span className="font-medium">Palletizer</span>
         </div>
         <div className="text-xs text-gray-500 mt-1">Automatic Palletizer</div>
      </div>
    </aside>
  );
}
