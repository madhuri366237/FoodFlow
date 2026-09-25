// Works with the backend's PageResponse: { page, totalPages, first, last, totalElements }.
export default function Pagination({ page, onChange }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <nav className="pagination" aria-label="Pagination">
      <button type="button" className="btn btn-outline btn-sm" disabled={page.first} onClick={() => onChange(page.page - 1)}>
        Previous
      </button>
      <span className="muted">
        Page {page.page + 1} of {page.totalPages} ({page.totalElements} results)
      </span>
      <button type="button" className="btn btn-outline btn-sm" disabled={page.last} onClick={() => onChange(page.page + 1)}>
        Next
      </button>
    </nav>
  );
}
