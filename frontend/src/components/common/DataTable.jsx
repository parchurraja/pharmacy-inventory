import React from 'react';
import { ChevronLeft, ChevronRight, Inbox } from 'lucide-react';
import { Button } from './Button';

export function DataTable({
  columns = [],
  data = [],
  loading = false,
  emptyTitle = 'No records found',
  emptyMessage = 'There are no items to display right now.',
  emptyAction = null,
  pagination = null, // { page, size, totalElements, totalPages, onPageChange }
}) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden flex flex-col">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-slate-50/75 border-b border-slate-200 text-xs font-semibold uppercase tracking-wider text-slate-500">
              {columns.map((col, idx) => (
                <th
                  key={col.key || idx}
                  className={`px-6 py-3.5 ${col.align === 'right' ? 'text-right' : col.align === 'center' ? 'text-center' : 'text-left'} ${col.className || ''}`}
                >
                  {col.title}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-sm">
            {loading ? (
              // Skeleton loading rows
              Array.from({ length: pagination?.size || 5 }).map((_, rIdx) => (
                <tr key={rIdx} className="animate-pulse">
                  {columns.map((col, cIdx) => (
                    <td key={cIdx} className="px-6 py-4">
                      <div className="h-4 bg-slate-100 rounded-md w-3/4" />
                    </td>
                  ))}
                </tr>
              ))
            ) : data.length === 0 ? (
              // Empty state
              <tr>
                <td colSpan={columns.length} className="px-6 py-12 text-center">
                  <div className="flex flex-col items-center justify-center max-w-sm mx-auto">
                    <div className="p-3 bg-slate-50 rounded-2xl border border-slate-200 text-slate-400 mb-3">
                      <Inbox className="w-8 h-8" />
                    </div>
                    <h4 className="text-base font-semibold text-slate-800">{emptyTitle}</h4>
                    <p className="text-xs text-slate-500 mt-1 leading-relaxed">{emptyMessage}</p>
                    {emptyAction && <div className="mt-4">{emptyAction}</div>}
                  </div>
                </td>
              </tr>
            ) : (
              // Actual data rows
              data.map((item, rIdx) => (
                <tr
                  key={item.id || rIdx}
                  className="hover:bg-slate-50/75 transition-colors group"
                >
                  {columns.map((col, cIdx) => (
                    <td
                      key={col.key || cIdx}
                      className={`px-6 py-4 text-slate-700 ${col.align === 'right' ? 'text-right' : col.align === 'center' ? 'text-center' : 'text-left'} ${col.className || ''}`}
                    >
                      {col.render ? col.render(item, rIdx) : item[col.key]}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination Bar */}
      {pagination && pagination.totalPages > 0 && (
        <div className="px-6 py-3.5 border-t border-slate-100 bg-slate-50/40 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500">
          <div>
            Showing{' '}
            <span className="font-semibold text-slate-700">
              {data.length === 0 ? 0 : pagination.page * pagination.size + 1}
            </span>{' '}
            to{' '}
            <span className="font-semibold text-slate-700">
              {Math.min((pagination.page + 1) * pagination.size, pagination.totalElements)}
            </span>{' '}
            of{' '}
            <span className="font-semibold text-slate-700">{pagination.totalElements}</span> entries
          </div>

          <div className="flex items-center gap-1.5">
            <Button
              variant="secondary"
              size="sm"
              disabled={pagination.page === 0 || loading}
              onClick={() => pagination.onPageChange(pagination.page - 1)}
              icon={ChevronLeft}
            >
              Previous
            </Button>

            <span className="px-3 py-1 font-medium text-slate-700">
              Page {pagination.page + 1} of {pagination.totalPages}
            </span>

            <Button
              variant="secondary"
              size="sm"
              disabled={pagination.page >= pagination.totalPages - 1 || loading}
              onClick={() => pagination.onPageChange(pagination.page + 1)}
            >
              Next
              <ChevronRight className="w-4 h-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
