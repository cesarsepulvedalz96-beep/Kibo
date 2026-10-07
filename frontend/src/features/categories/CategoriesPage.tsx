import { Navigate } from 'react-router-dom';

/** Keep existing bookmarks working while category management stays inside Products. */
export default function CategoriesPage() {
  return <Navigate to="/products?categories=open" replace />;
}
