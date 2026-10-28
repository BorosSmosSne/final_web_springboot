package com.setec.controller;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.setec.DAO.PostProductDAO;
import com.setec.DAO.PutProductDAO;
import com.setec.entities.Category;
import com.setec.entities.Product;
import com.setec.repos.CategoryRepos;
import com.setec.repos.ProductRepo;

@RestController
@RequestMapping("api/product")
public class MyController {

	private static final String NO_IMAGE = "/static/no-image.png";

	private final ProductRepo productRepo;
	private final CategoryRepos categoryRepos;

	MyController(ProductRepo productRepo, CategoryRepos categoryRepos) {
		this.productRepo = productRepo;
		this.categoryRepos = categoryRepos;
	}

	// ---------- helpers ----------

	private String saveImage(MultipartFile file) throws Exception {
		String uploadDir = new File("images/static").getAbsolutePath();
		File dir = new File(uploadDir);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		String original = Objects.requireNonNull(file.getOriginalFilename());
		String fileName = UUID.randomUUID() + "-" + original;
		file.transferTo(new File(Paths.get(uploadDir, fileName).toString()));
		return "/static/" + fileName;
	}

	private void deleteImage(String imgUrl) {
		if (imgUrl != null && imgUrl.startsWith("/static/") && !imgUrl.equals(NO_IMAGE)) {
			new File("images" + imgUrl).delete();
		}
	}

	private static boolean hasFile(MultipartFile f) {
		return f != null && !f.isEmpty();
	}

	// ---------- READ ----------

	@GetMapping
	public Object getAll() {
		return productRepo.findAll();     // empty list [] when no products
	}

	@GetMapping("{id}")
	public Object getById(@PathVariable("id") Integer id) {
		var product = productRepo.findById(id);
		if (product.isPresent()) {
			return product.get();
		}
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("Message", "Product ID " + id + " Not Found"));
	}

	@GetMapping("name/{name}")
	public Object getByName(@PathVariable("name") String name) {
		List<Product> product = productRepo.findByName(name);
		if (product.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("Message", "Product name " + name + " not found"));
		}
		return product;
	}

	// NEW: partial, case-insensitive search -> GET /api/product/search?q=latte
	@GetMapping("search")
	public Object search(@RequestParam("q") String q) {
		return productRepo.findByNameContainingIgnoreCase(q);
	}

	@GetMapping("category/{cid}")
	public Object getByCategory(@PathVariable("cid") Integer cid) {
		return productRepo.findByCategoryId(cid);   // empty list [] when none
	}

	// ---------- CREATE ----------

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Object postProduct(@ModelAttribute PostProductDAO product) throws Exception {

		if (product.getName() == null || product.getName().isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("Message", "name is required"));
		}
		if (product.getPrice() == null || product.getPrice() <= 0) {
			return ResponseEntity.badRequest().body(Map.of("Message", "price must be greater than 0"));
		}

		Product pro = new Product();
		pro.setName(product.getName().trim());
		pro.setPrice(product.getPrice());
		pro.setQty(product.getQty() == null ? 0 : product.getQty());
		pro.setDescription(product.getDescription());
		pro.setAvailable(product.getAvailable() == null ? Boolean.TRUE : product.getAvailable());

		if (product.getCategoryId() != null) {
			Category cat = categoryRepos.findById(product.getCategoryId()).orElse(null);
			if (cat == null) {
				return ResponseEntity.badRequest()
						.body(Map.of("Message", "Category ID " + product.getCategoryId() + " not found"));
			}
			pro.setCategory(cat);
		}

		pro.setImgUrl(hasFile(product.getImageFile()) ? saveImage(product.getImageFile()) : NO_IMAGE);

		productRepo.save(pro);
		return ResponseEntity.status(HttpStatus.CREATED).body(pro);
	}

	// ---------- UPDATE ----------

	@PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Object putProduct(@ModelAttribute PutProductDAO product) throws Exception {

		if (product.getId() == null) {
			return ResponseEntity.badRequest().body(Map.of("Message", "id is required"));
		}

		var p = productRepo.findById(product.getId());
		if (p.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("Message", "Product ID " + product.getId() + " Not Found"));
		}

		Product update = p.get();

		if (hasFile(product.getImageFile())) {
			deleteImage(update.getImgUrl());
			update.setImgUrl(saveImage(product.getImageFile()));
		}
		if (product.getName() != null && !product.getName().isBlank()) {
			update.setName(product.getName().trim());
		}
		if (product.getPrice() != null) {
			update.setPrice(product.getPrice());
		}
		if (product.getQty() != null) {
			update.setQty(product.getQty());
		}
		if (product.getDescription() != null) {
			update.setDescription(product.getDescription());
		}
		if (product.getAvailable() != null) {
			update.setAvailable(product.getAvailable());
		}
		if (product.getCategoryId() != null) {
			Category cat = categoryRepos.findById(product.getCategoryId()).orElse(null);
			if (cat == null) {
				return ResponseEntity.badRequest()
						.body(Map.of("Message", "Category ID " + product.getCategoryId() + " not found"));
			}
			update.setCategory(cat);
		}

		productRepo.save(update);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("Message", "Update Successful...!"));
	}

	// NEW: sold-out toggle -> PATCH /api/product/{id}/availability  body: {"available": false}
	@PatchMapping("{id}/availability")
	public Object setAvailability(@PathVariable("id") Integer id, @RequestBody Map<String, Boolean> body) {
		var p = productRepo.findById(id);
		if (p.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("Message", "Product ID " + id + " Not Found"));
		}
		Boolean available = body.get("available");
		if (available == null) {
			return ResponseEntity.badRequest().body(Map.of("Message", "available (true/false) is required"));
		}
		Product product = p.get();
		product.setAvailable(available);
		return productRepo.save(product);
	}

	// ---------- DELETE ----------

	@DeleteMapping("/{id}")
	public Object deleteById(@PathVariable("id") Integer id) {
		var product = productRepo.findById(id);
		if (product.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("Message", "Product ID: " + id + " not found"));
		}
		try {
			productRepo.delete(product.get());
		} catch (DataIntegrityViolationException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("Message",
					"Product is used in a cart or order. Set available=false instead of deleting."));
		}
		deleteImage(product.get().getImgUrl());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("Message", "Delete Successful...!"));
	}
}