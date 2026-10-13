import ProductOperations from "@/components/ProductOperations";

export default async function ProductOperationsPage({params}:{params:Promise<{id:string}>}){
  const {id}=await params;
  return <ProductOperations productId={id}/>;
}
