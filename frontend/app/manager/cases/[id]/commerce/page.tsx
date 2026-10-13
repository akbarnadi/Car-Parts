import CaseCommerce from "@/components/CaseCommerce";export default async function CommercePage({params}:{params:Promise<{id:string}>}){const {id}=await params;return <CaseCommerce caseId={id}/>}
