package com.setec.controller;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.setec.DAO.PostProductDAO;
import com.setec.DAO.PutProductDAO;
import com.setec.entities.Product;
import com.setec.repos.ProductRepo;


@RestController
@RequestMapping("api/product")

public class MyController {
	
	private final ProductRepo productRepo;

	MyController(ProductRepo productRepo) {
	    this.productRepo = productRepo;
	}
	
	@GetMapping
	public Object getAll() {
		var product=productRepo.findAll();
		if(product.size()==0) {
			return ResponseEntity
					.status(HttpStatus.NOT_FOUND)
					.body(Map.of("Message","Product not Found"));
		}else {
			return product;
		}
	}
	
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Object postProduct(@ModelAttribute PostProductDAO product)throws Exception {
		var file=product.getImageFile();
		
		String uploadDir=new File("images/static").getAbsolutePath();
		File dir=new File(uploadDir);
		if(!dir.exists()) {
			dir.mkdirs();
		}
		
		String extension=Objects.requireNonNull(file.getOriginalFilename());
		String fileName=UUID.randomUUID()+"-"+extension;
		String filePath=Paths.get(uploadDir, fileName).toString();
		
		file.transferTo(new File(filePath));
		
		Product pro=new Product();
		pro.setName(product.getName());
		pro.setQty(product.getQty());
		pro.setPrice(product.getPrice());
		pro.setImgUrl("/static/"+fileName);
		
		productRepo.save(pro);
		
		return ResponseEntity.status(201).body(pro);
	}
	@GetMapping("{id}")
	public Object getById(@PathVariable("id") Integer id) {
		var product =productRepo.findById(id);
		if(product.isPresent()) {
			return product.get();
		}else {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("Message","Product ID "+ id +" Not Found"));
		}
	}
	
	@GetMapping("name/{name}")
	public Object getByName(@PathVariable("name") String name) {
		List<Product> product=productRepo.findByName(name);
		
		if(product.size()==0) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("Message","Product name "+ name +" not found"));
		}else {
			return product;
		}
	}
	
	@DeleteMapping("/{id}")
	public Object deleteById(@PathVariable("id") Integer id)
	{
		var product =productRepo.findById(id);
		if(product.isPresent()) {
			
			new File("images/"+product.get().getImgUrl()).delete();
			productRepo.delete(product.get());
			return ResponseEntity.status(HttpStatus.ACCEPTED)
					.body(Map.of("Message : ","Delete Successful...!"));
		}
		
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("Message:","Product ID: "+id+" not found"));
	}
	
	
	@PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Object putProduct(@ModelAttribute PutProductDAO product)throws Exception {
		
		Integer id=product.getId();
		var p =productRepo.findById(id);
		
		if(p.isPresent()) {
			
			var update=p.get();
			
			if(product.getImageFile()!=null) {
				
				var file=product.getImageFile();
				
				String uploadDir=new File("images/static").getAbsolutePath();
				File dir=new File(uploadDir);
				if(!dir.exists()) {
					dir.mkdirs();
				}
				
				String extension=Objects.requireNonNull(file.getOriginalFilename());
				String fileName=UUID.randomUUID()+"-"+extension;
				String filePath=Paths.get(uploadDir, fileName).toString();
				
				new File("images/"+update.getImgUrl()).delete();
				file.transferTo(new File(filePath));
				
				update.setImgUrl("/static/"+fileName);
			}
			
			update.setName(product.getName());
			update.setPrice(product.getPrice());
			update.setQty(product.getQty());
			
			productRepo.save(update);
			
			return ResponseEntity.status(HttpStatus.ACCEPTED)
					.body(Map.of("Message : ","Update Successful...!"));
		}
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("Message","Product ID "+ id +" Not Found"));
	}
	@GetMapping("category/{cid}")
	public Object getByCategory	(@PathVariable("cid") Integer cid) {
		List<Product> products = productRepo.findByCategoryId(cid);
		if(products.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("Message", "No products found for Category ID " + cid));
		}
		return products;
	}
}
