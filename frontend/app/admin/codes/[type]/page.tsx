import LookupAdmin from "@/components/LookupAdmin";export default async function CodeTable({params}:{params:Promise<{type:string}>}){const {type}=await params;return <LookupAdmin type={type}/>}
